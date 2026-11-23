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