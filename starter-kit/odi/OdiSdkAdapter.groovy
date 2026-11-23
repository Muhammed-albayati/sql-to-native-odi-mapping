/* Experimental ODI SDK adapter. No credentials or company-specific rules.
 * ODI 12c runtime is required. Do not use in production before validation.
 */
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