package hk.polyu.comp.jaid.fixaction;

/**
 * Created by Ls CHEN
 */
public class Schemas {
    protected static final String SNIPPET = "${snippet}";
    protected static final String OLD_STMT = "${old_stmt}";
    protected static final String OLD_EXP = "${old_exp}";
    protected static final String FAIL = "${fail}";

    // TODO: Refactor: every SCHEMA should become an independent object that can do some pre detection when building fix actions.
    public enum Schema {

        SCHEMA_A(SNIPPET + "\n" + OLD_STMT, "A"),
        SCHEMA_B("if(" + FAIL + "){\n" + SNIPPET + "\n}\n" + OLD_STMT, "B"),
        SCHEMA_C("if(!(" + FAIL + ")){\n" + OLD_STMT + "\n}", "C"),
        SCHEMA_D("if(" + FAIL + "){\n" + SNIPPET + "\n}else{\n" + OLD_STMT + "\n}", "D"),
        SCHEMA_E(SNIPPET, "E");

        //        IF_OR_SCHEMA("(" + OLD_EXP + ") || (" + FAIL + ")"),
//        IF_AND_SCHEMA("(" + OLD_EXP + ") && (" + FAIL + ")");
        private String schema;
        private String idx;

        Schema(String schema, String idx) {
            this.schema = schema;
            this.idx = idx;
        }

        public String getSchemaOrder() {
            return idx;
        }

        public String getSchema() {
            return schema;
        }
    }
}
