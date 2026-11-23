// Parser-only smoke contracts. Run from starter-kit/ with local Groovy.
def loader = new GroovyClassLoader(this.class.classLoader)
loader.parseClass(new File('core/RestrictedSqlParser.groovy'))
def parser = loader.loadClass('RestrictedSqlParser').newInstance()
def s = parser.parse(new File('examples/simple_select.sql').text)
assert s.sources.size() == 1
assert s.outputs.collect { it.output } == ['ORDER_ID','NET_AMOUNT']
assert s.filters.size() == 1
def j = parser.parse(new File('examples/inner_join.sql').text)
assert j.sources.size() == 2
assert j.join.operator == '='
assert j.filters.size() == 2
assert j.outputs.size() == 3
int rejects = 0
[
 'DELETE FROM SALES_ORDERS',
 'SELECT * FROM SALES_ORDERS S',
 'SELECT S.ORDER_ID FROM SALES_ORDERS S LEFT JOIN OTHER P ON S.ID = P.ID',
 'SELECT S.ORDER_ID FROM SALES_ORDERS S; DELETE FROM OTHER',
 'SELECT S.ORDER_ID FROM SALES_ORDERS S WHERE S.ID = 1 OR S.ID = 2',
 new File('examples/unsupported_group_by.sql').text
].each {
    try {
        parser.parse(it)
        assert false : 'Unsupported SQL was accepted'
    } catch (IllegalArgumentException expected) {
        rejects++
    }
}
assert rejects == 6
println 'Parser contracts: 2 positive, 6 negative passed.'