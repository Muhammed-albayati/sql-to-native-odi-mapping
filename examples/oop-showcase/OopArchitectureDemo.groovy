/*
 * OOP design demonstration for a SQL-to-native-ODI mapping generator.
 *
 * Public, synthetic, ODI-independent educational example.
 * This is NOT a SQL parser, an ODI SDK implementation, or a mapping generator.
 * It makes no database, repository, network, or filesystem changes.
 * Compatible in intent with Groovy 2.5 / Java 8 syntax; not runtime-tested here.
 */

import java.util.Locale

// A typed representation of a plan already produced by a PRIVATE SQL parser.
// No SQL text is parsed or converted in this example.
enum StepKind {
    SOURCE, FILTER, JOIN, AGGREGATE, TARGET
}

class PlanStep {
    final String id
    final StepKind kind
    final List<String> inputs
    final Map<String, String> properties

    PlanStep(String id, StepKind kind, List<String> inputs, Map<String, String> properties) {
        this.id = id
        this.kind = kind
        this.inputs = Collections.unmodifiableList(new ArrayList<String>(inputs))
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<String, String>(properties))
    }
}

class MappingPlan {
    final String mappingName
    final List<PlanStep> steps

    MappingPlan(String mappingName, List<PlanStep> steps) {
        this.mappingName = mappingName
        this.steps = Collections.unmodifiableList(new ArrayList<PlanStep>(steps))
    }
}

// Validate the graph BEFORE an adapter performs any repository mutation.
// This lightweight validation does not establish Oracle SQL semantic equivalence.
class MappingPlanValidator {
    private static final Set<String> REQUIRED_PROPERTIES = [
        'SOURCE': ['datastore'],
        'FILTER': ['predicate'],
        'JOIN': ['predicate', 'type'],
        'AGGREGATE': ['groupBy', 'measure'],
        'TARGET': ['datastore']
    ].keySet() as Set<String>

    void validate(MappingPlan plan) {
        if (!(plan.mappingName ==~ /[A-Za-z][A-Za-z0-9_]{0,99}/)) {
            throw new IllegalArgumentException('Invalid mapping name')
        }
        if (plan.steps == null || plan.steps.isEmpty()) {
            throw new IllegalArgumentException('A mapping plan must contain steps')
        }

        Set<String> seen = new LinkedHashSet<String>()
        int targetCount = 0
        for (PlanStep step : plan.steps) {
            if (!(step.id ==~ /[A-Za-z][A-Za-z0-9_]{0,99}/)) {
                throw new IllegalArgumentException('Invalid step id')
            }
            if (step.kind == null || !REQUIRED_PROPERTIES.contains(step.kind.name())) {
                throw new IllegalArgumentException('Unsupported step type')
            }
            if (seen.contains(step.id)) {
                throw new IllegalArgumentException('Duplicate step id: ' + step.id)
            }

            int expectedInputs = step.kind == StepKind.SOURCE ? 0 :
                step.kind == StepKind.JOIN ? 2 : 1
            if (step.inputs.size() != expectedInputs || step.inputs.toSet().size() != step.inputs.size()) {
                throw new IllegalArgumentException('Invalid input count for ' + step.id)
            }
            for (String input : step.inputs) {
                if (!seen.contains(input)) {
                    throw new IllegalArgumentException('Missing/forward input: ' + input)
                }
            }
            List<String> required = [
                SOURCE: ['datastore'], FILTER: ['predicate'],
                JOIN: ['predicate', 'type'], AGGREGATE: ['groupBy', 'measure'],
                TARGET: ['datastore']
            ][step.kind.name()]
            for (String property : required) {
                if (!step.properties[property]?.trim()) {
                    throw new IllegalArgumentException('Missing property ' + property + ' on ' + step.id)
                }
            }
            if (step.kind == StepKind.JOIN &&
                !(step.properties.type.toUpperCase(Locale.ENGLISH) in ['INNER', 'LEFT', 'RIGHT'])) {
                throw new IllegalArgumentException('Unsupported join type on ' + step.id)
            }
            if (step.kind == StepKind.TARGET) {
                targetCount++
            }
            seen.add(step.id)
        }
        if (targetCount != 1 || plan.steps.last().kind != StepKind.TARGET) {
            throw new IllegalArgumentException('Exactly one final TARGET is required')
        }
    }
}

// Port: replace this with a PRIVATE ODI SDK adapter in a real application.
// Keeping ODI types out of this contract makes the planning layer testable.
interface MappingComponentPort {
    void createMapping(String name)
    void addComponent(PlanStep step)
    void validate()
}

// Adapter used ONLY by the public demo. It never calls ODI or a database.
class ConsoleMappingComponentAdapter implements MappingComponentPort {
    void createMapping(String name) {
        println('DEMO mapping: ' + name)
    }
    void addComponent(PlanStep step) {
        println('  ' + step.kind + ' ' + step.id +
            ' <- ' + (step.inputs.isEmpty() ? '[none]' : step.inputs.join(', ')))
    }
    void validate() {
        println('DEMO complete: graph checks only (no ODI mapping created)')
    }
}

class MappingGenerationService {
    private final MappingPlanValidator validator
    private final MappingComponentPort adapter

    MappingGenerationService(MappingPlanValidator validator, MappingComponentPort adapter) {
        this.validator = validator
        this.adapter = adapter
    }

    void generate(MappingPlan plan) {
        validator.validate(plan)  // fail fast before interacting with the adapter
        adapter.createMapping(plan.mappingName)
        for (PlanStep step : plan.steps) {
            adapter.addComponent(step)
        }
        adapter.validate()
    }
}

// Synthetic example. These are plan descriptors, NOT executable SQL.
MappingPlan example = new MappingPlan('MAP_PRODUCT_SALES_DEMO', [
    new PlanStep('SALES', StepKind.SOURCE, [], [datastore: 'SALES_ORDERS']),
    new PlanStep('VALID_SALES', StepKind.FILTER, ['SALES'], [predicate: 'STATUS = COMPLETED']),
    new PlanStep('SUM_SALES', StepKind.AGGREGATE, ['VALID_SALES'],
        [groupBy: 'PRODUCT_ID', measure: 'SUM(NET_AMOUNT)']),
    new PlanStep('PRODUCTS', StepKind.SOURCE, [], [datastore: 'DIM_PRODUCT']),
    new PlanStep('PRODUCT_JOIN', StepKind.JOIN, ['SUM_SALES', 'PRODUCTS'],
        [type: 'INNER', predicate: 'PRODUCT_ID = PRODUCT_ID']),
    new PlanStep('RESULT', StepKind.TARGET, ['PRODUCT_JOIN'],
        [datastore: 'FACT_PRODUCT_SALES'])
])

new MappingGenerationService(
    new MappingPlanValidator(),
    new ConsoleMappingComponentAdapter()
).generate(example)
