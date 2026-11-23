// EXPERIMENTAL STARTER / NOT ODI-RUNTIME TESTED
import oracle.odi.domain.mapping.Mapping
import oracle.odi.domain.mapping.component.DatastoreComponent
import oracle.odi.domain.mapping.component.FilterComponent
import oracle.odi.domain.mapping.component.JoinComponent
import oracle.odi.domain.mapping.finder.IMappingFinder
import oracle.odi.domain.model.OdiDataStore
import oracle.odi.domain.model.finder.IOdiDataStoreFinder
import oracle.odi.domain.project.OdiProject
import oracle.odi.domain.project.OdiFolder
import oracle.odi.domain.project.finder.IOdiProjectFinder
import oracle.odi.domain.project.finder.IOdiFolderFinder
import oracle.odi.domain.topology.OdiContext
import oracle.odi.domain.topology.finder.IOdiContextFinder
import oracle.odi.core.persistence.transaction.support.DefaultTransactionDefinition

/*
 * Public starter: restricted, fail-fast SQL parser (no ODI dependency).
 * This is independently authored sample code, not the production parser.
 */
class StarterSource {
    String schema
    String table
    String alias
}
class StarterColumn {
    String alias
    String column
    String output
}
class StarterPredicate {
    String leftAlias
    String leftColumn
    String operator
    String rhs
}
class StarterSelectPlan {
    List<StarterSource> sources = []
    List<StarterColumn> outputs = []
    StarterPredicate join
    List<StarterPredicate> filters = []
}
class RestrictedSqlParser {
    private static final String ID = '[A-Za-z][A-Za-z0-9_]*'
    private static final String TABLE = '(' + ID + ')(?:\\.(' + ID + '))?'

    private static String maskStrings(String sql) {
        StringBuilder b = new StringBuilder()
        boolean quote = false
        for (int i=0; i<sql.length(); i++) {
            char ch = sql.charAt(i)
            if (ch == (char)39) {
                if (quote && i+1 < sql.length() && sql.charAt(i+1) == (char)39) {
                    b.append('  '); i++; continue
                }
                quote = !quote
                b.append(' ')
            } else b.append(quote ? ' ' : ch)
        }
        if (quote) throw new IllegalArgumentException('Unterminated string')
        return b.toString()
    }
    private static int keyword(String sql, String word) {
        def matcher = (maskStrings(sql) =~ ('(?i)\\b' + word.replace(' ', '\\s+') + '\\b'))
        return matcher.find() ? matcher.start() : -1
    }
    private static StarterSource parseSource(String text) {
        def m = (text.trim() =~ ('(?i)^' + TABLE + '(?:\\s+(?:AS\\s+)?(' + ID + '))?$'))
        if (!m.matches()) throw new IllegalArgumentException('Unsupported FROM source: '+text)
        String tableName = (m.group(2) ?: m.group(1)).toUpperCase(Locale.ENGLISH)
        new StarterSource(schema:m.group(2) ? m.group(1).toUpperCase(Locale.ENGLISH) : null,
            table:tableName, alias:(m.group(3) ?: tableName).toUpperCase(Locale.ENGLISH))
    }
    private static StarterPredicate parseCondition(String text) {
        def m = (text.trim() =~ ('(?is)^('+ID+')\\.('+ID+')\\s*(=|<>|>=|<=|>|<)\\s*(' +
            "'(?:''|[^'])*'|-?[0-9]+(?:\\.[0-9]+)?|"+ID+'\\.'+ID+')$'))
        if (!m.matches()) throw new IllegalArgumentException('Unsupported condition: '+text)
        new StarterPredicate(leftAlias:m.group(1).toUpperCase(Locale.ENGLISH),
            leftColumn:m.group(2).toUpperCase(Locale.ENGLISH), operator:m.group(3), rhs:m.group(4))
    }
    private static void requireAlias(String alias, Set known) {
        if (!known.contains(alias.toUpperCase(Locale.ENGLISH)))
            throw new IllegalArgumentException('Unknown alias: '+alias)
    }
    StarterSelectPlan parse(String input) {
        if (input == null || input.length() > 10000)
            throw new IllegalArgumentException('SQL required; maximum 10000 characters')
        String sql = input.trim().replaceFirst(';\\s*$', '')
        if (sql.contains(';') || sql.contains('--') || sql.contains('/*') || sql.contains('*/'))
            throw new IllegalArgumentException('Comments/multiple SQL statements rejected')
        if (!(sql ==~ '(?is)^SELECT\\s+.+'))
            throw new IllegalArgumentException('SELECT only')
        ['GROUP BY','HAVING','ORDER BY','UNION','WITH','DISTINCT',
         'LEFT JOIN','RIGHT JOIN','FULL JOIN','CROSS JOIN','EXISTS'].each {
            if (keyword(sql,it)>=0) throw new IllegalArgumentException('Unsupported SQL: '+it)
        }
        int f = keyword(sql,'FROM')
        if (f < 0) throw new IllegalArgumentException('Missing FROM')
        String selectText = sql.substring(6,f).trim()
        String tail = sql.substring(f+4).trim()
        int w = keyword(tail,'WHERE')
        String fromText = (w<0 ? tail : tail.substring(0,w)).trim()
        String whereText = w<0 ? '' : tail.substring(w+5).trim()
        if (!selectText || !fromText || (w>=0 && !whereText))
            throw new IllegalArgumentException('Incomplete SQL')
        StarterSelectPlan plan = new StarterSelectPlan()
        int j = keyword(fromText,'INNER JOIN')
        if (j<0) j=keyword(fromText,'JOIN')
        if (j<0) plan.sources << parseSource(fromText)
        else {
            String tailJoin = fromText.substring(j).trim()
                .replaceFirst('(?is)^(?:INNER\\s+)?JOIN\\s+','')
            int on = keyword(tailJoin,'ON')
            if (on<0) throw new IllegalArgumentException('JOIN requires ON')
            plan.sources << parseSource(fromText.substring(0,j))
            plan.sources << parseSource(tailJoin.substring(0,on))
            plan.join = parseCondition(tailJoin.substring(on+2))
            if (plan.join.operator != '=' ||
                !(plan.join.rhs ==~ ('(?i)'+ID+'\\.'+ID)))
                throw new IllegalArgumentException('JOIN requires column = column')
        }
        Set aliases = plan.sources.collect { it.alias } as Set
        if (aliases.size() != plan.sources.size())
            throw new IllegalArgumentException('Duplicate source alias')
        if (plan.sources.size()==2 && plan.sources[0].table==plan.sources[1].table)
            throw new IllegalArgumentException('Self joins not included')
        if (plan.join) {
            requireAlias(plan.join.leftAlias,aliases)
            String rightAlias = plan.join.rhs.split('\\.')[0].toUpperCase(Locale.ENGLISH)
            requireAlias(rightAlias,aliases)
            if (rightAlias == plan.join.leftAlias)
                throw new IllegalArgumentException('JOIN must reference both sources')
        }
        Set usedOutputs = [] as Set
        selectText.split(',').each { part ->
            def m = (part.trim() =~ ('(?is)^('+ID+')\\.('+ID+')(?:\\s+(?:AS\\s+)?('+ID+'))?$'))
            if (!m.matches()) throw new IllegalArgumentException('Qualified column required: '+part)
            def col = new StarterColumn(alias:m.group(1).toUpperCase(Locale.ENGLISH),
                column:m.group(2).toUpperCase(Locale.ENGLISH),
                output:(m.group(3) ?: m.group(2)).toUpperCase(Locale.ENGLISH))
            requireAlias(col.alias,aliases)
            if (!usedOutputs.add(col.output))
                throw new IllegalArgumentException('Duplicate output: '+col.output)
            plan.outputs << col
        }
        if (whereText) {
            if (keyword(whereText,'OR')>=0) throw new IllegalArgumentException('OR not supported')
            def matcher = (maskStrings(whereText) =~ '(?i)\\s+AND\\s+')
            int start=0
            List clauses=[]
            while (matcher.find()) { clauses << whereText.substring(start,matcher.start()); start=matcher.end() }
            clauses << whereText.substring(start)
            clauses.each {
                def c = parseCondition(it)
                requireAlias(c.leftAlias,aliases)
                if (c.rhs ==~ ('(?i)'+ID+'\\.'+ID))
                    requireAlias(c.rhs.split('\\.')[0],aliases)
                plan.filters << c
            }
        }
        return plan
    }
}

/* Experimental ODI SDK adapter. No credentials or company-specific rules.
 * ODI 12c runtime is required. Do not use in production before validation.
 */

class StarterOdiSdkAdapter {
    final def odiInstance
    final Map config

    StarterOdiSdkAdapter(def odiInstance, Map config) {
        this.odiInstance = odiInstance
        this.config = config
    }

    private static void required(String key, String value) {
        if (value == null || !(value ==~ /[A-Za-z][A-Za-z0-9_]{0,99}/)) {
            throw new IllegalArgumentException('Missing/invalid configuration: ' + key)
        }
    }

    private String modelCode(StarterSource s) {
        String model = s.schema == null ? config.defaultSourceModel : config.sourceModels[s.schema]
        if (!model) throw new IllegalArgumentException('No ODI model configured for '+(s.schema ?: 'unqualified sources'))
        required('source model', model)
        return model
    }

    private static String formatPredicate(StarterPredicate p, Map<String,String> components) {
        String lhs = components[p.leftAlias] + '.' + p.leftColumn
        String rhs = p.rhs
        if (p.rhs ==~ /(?i)[A-Za-z][A-Za-z0-9_]*\.[A-Za-z][A-Za-z0-9_]*/) {
            String[] parts = p.rhs.toUpperCase(Locale.ENGLISH).split('\\.')
            rhs = components[parts[0]] + '.' + parts[1]
        }
        return lhs + ' ' + p.operator + ' ' + rhs
    }

    void generate(StarterSelectPlan plan) {
        ['projectCode','folderName','targetModel','targetDatastore','mappingName','contextCode'].each {
            required(it, config[it] as String)
        }
        if (!(config.writeEnabled == Boolean.TRUE)) {
            throw new IllegalStateException('Refusing repository write: writeEnabled must be explicitly true')
        }
        if (odiInstance == null) throw new IllegalArgumentException('odiInstance is null')

        def tm = odiInstance.getTransactionManager()
        def tme = odiInstance.getTransactionalEntityManager()
        def tx = tm.getTransaction(new DefaultTransactionDefinition())
        try {
            def project = ((IOdiProjectFinder)tme.getFinder(OdiProject.class)).findByCode(config.projectCode)
            if (project == null) throw new IllegalArgumentException('ODI project not found')
            def folders = ((IOdiFolderFinder)tme.getFinder(OdiFolder.class)).findByName(
                config.folderName, config.projectCode)
            if (folders == null || folders.size() != 1) throw new IllegalArgumentException('Expected exactly one ODI folder')
            def folder = folders.iterator().next()
            def existing = ((IMappingFinder)tme.getFinder(Mapping.class)).findByName(folder, config.mappingName)
            if (existing != null) throw new IllegalArgumentException('Mapping already exists; nothing overwritten')

            def finder = (IOdiDataStoreFinder)tme.getFinder(OdiDataStore.class)
            def targetStore = finder.findByName(config.targetDatastore, config.targetModel)
            if (targetStore == null) throw new IllegalArgumentException('Target datastore not found')
            def mapping = new Mapping(config.mappingName, folder)
            tme.persist(mapping)

            Map<String,Object> components = [:]
            Map<String,String> componentNames = [:]
            Map<String,Object> metadata = [:]
            plan.sources.each { s ->
                def store = finder.findByName(s.table, modelCode(s))
                if (store == null) throw new IllegalArgumentException('Source datastore not found: '+s.table)
                def comp = new DatastoreComponent(mapping, store, false)
                components[s.alias] = comp
                componentNames[s.alias] = comp.getName()
                metadata[s.alias] = store
            }
            plan.outputs.each { c ->
                if (metadata[c.alias].getColumn(c.column) == null) {
                    throw new IllegalArgumentException('Source column missing: '+c.alias+'.'+c.column)
                }
                if (targetStore.getColumn(c.output) == null) {
                    throw new IllegalArgumentException('Target column missing: '+c.output)
                }
            }
            def root = components[plan.sources[0].alias]
            if (plan.join != null) {
                def right = components[plan.sources[1].alias]
                def condition = formatPredicate(plan.join, componentNames)
                root = JoinComponent.joinSources('JOIN_1', root, right, condition)
            }
            if (!plan.filters.isEmpty()) {
                def filter = new FilterComponent(mapping,'FILTER_1')
                root.connectTo(filter)
                filter.setFilterCondition(plan.filters.collect { formatPredicate(it, componentNames) }.join(' AND '))
                root = filter
            }
            def target = new DatastoreComponent(mapping, targetStore, false)
            root.connectTo(target)
            target.setIntegrationType(DatastoreComponent.INTEGRATION_TYPE_APPEND)
            plan.outputs.each { c ->
                DatastoreComponent.findAttributeForColumn(target, targetStore.getColumn(c.output))
                    .setExpressionText(componentNames[c.alias] + '.' + c.column)
            }
            List logicalIssues = []
            if (!mapping.validate(logicalIssues,false)) {
                throw new IllegalStateException('ODI logical validation failed: '+logicalIssues)
            }
            def context = ((IOdiContextFinder)tme.getFinder(OdiContext.class)).findByCode(config.contextCode)
            if (context == null) throw new IllegalArgumentException('ODI context not found')
            def physical = mapping.createPhysicalDesign('Physical',context)
            List syncIssues = []
            physical.syncWithLogicalMapping(syncIssues)
            if (!syncIssues.isEmpty()) println('Physical synchronization issues: '+syncIssues)
            if (config.ikmName) {
                def targets = physical.getTargetNodes()
                if (targets == null || targets.size() != 1) {
                    throw new IllegalStateException('Expected one physical target node')
                }
                targets[0].setIKMByName(config.ikmName)
            }
            List physicalIssues = []
            if (!physical.validate(physicalIssues,false)) {
                throw new IllegalStateException('ODI physical validation failed: '+physicalIssues)
            }
            tme.persist(mapping)
            tm.commit(tx)
            println('Created ODI native mapping: '+config.mappingName)
        } catch (Throwable failure) {
            tm.rollback(tx)
            throw failure
        }
    }
}

/*
 * ODI Procedure entry point. Run as Groovy in an ODI 12c Procedure.
 * No repository write happens until writeEnabled is explicitly true.
 */
Map starterConfig = [
    mappingName       : 'MAP_EXAMPLE_SALES',
    targetDatastore   : 'FACT_EXAMPLE_SALES',
    projectCode       : 'DEMO_PROJECT',
    folderName        : 'DEMO_FOLDER',
    targetModel       : 'DEMO_TARGET_MODEL',
    defaultSourceModel: 'DEMO_SOURCE_MODEL',
    sourceModels      : [ODS:'DEMO_SOURCE_MODEL',EDW:'DEMO_DW_MODEL'],
    contextCode       : 'DEMO_CONTEXT',
    ikmName           : null, // Optional: exact installed IKM name
    writeEnabled      : false // SAFE DEFAULT: preview only
]
String starterSql = '''
SELECT S.ORDER_ID AS ORDER_ID,
       S.NET_AMOUNT AS NET_AMOUNT
FROM SALES_ORDERS S
WHERE S.STATUS = 'COMPLETED'
'''
StarterSelectPlan plan = new RestrictedSqlParser().parse(starterSql)
println('SQL plan: '+plan.sources.size()+' source(s), '+plan.outputs.size()+
    ' output(s), '+plan.filters.size()+' filter term(s).')
if (starterConfig.writeEnabled == Boolean.TRUE) {
    // odiRef is provided by the ODI Groovy Procedure runtime.
    new StarterOdiSdkAdapter(odiRef.getOdiInstance(),starterConfig).generate(plan)
} else {
    println('PREVIEW ONLY: no ODI repository changes.')
}
