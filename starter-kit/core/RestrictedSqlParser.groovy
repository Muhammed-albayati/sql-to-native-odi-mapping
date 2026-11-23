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
            def matcher = (maskStrings(whereText) =~ '(?i)\\bAND\\b')
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