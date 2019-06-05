package hk.polyu.comp.jaid.monitor.state;

import com.sun.jdi.*;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.PrimitiveType;

import static hk.polyu.comp.jaid.monitor.state.DebuggerEvaluationResult.ReferenceDebuggerEvaluationResult.NULL_REFERENCE_ID;
import static hk.polyu.comp.jaid.monitor.state.DebuggerEvaluationResult.ReferenceDebuggerEvaluationResult.NULL_REFERENCE_STRING;
import static hk.polyu.comp.jaid.monitor.state.DebuggerEvaluationResult.ReferenceDebuggerEvaluationResult.NULL_REFERENCE_TYPE;

/**
 * Created by Max PEI.
 */
public abstract class DebuggerEvaluationResult {

    public final static BooleanDebuggerEvaluationResult BOOLEAN_DEBUGGER_EVALUATION_RESULT_TRUE = new BooleanDebuggerEvaluationResult(true);
    public final static BooleanDebuggerEvaluationResult BOOLEAN_DEBUGGER_EVALUATION_RESULT_FALSE = new BooleanDebuggerEvaluationResult(false);
    public final static ReferenceDebuggerEvaluationResult REFERENCE_DEBUGGER_EVALUATION_RESULT_NULL = new ReferenceDebuggerEvaluationResult(NULL_REFERENCE_ID, NULL_REFERENCE_STRING, NULL_REFERENCE_TYPE);
    public final static SemanticErrorDebuggerEvaluationResult DEBUGGER_EVALUATION_RESULT_SEMANTIC_ERROR = new SemanticErrorDebuggerEvaluationResult();
    public final static SyntaxErrorDebuggerEvaluationResult DEBUGGER_EVALUATION_RESULT_SYNTAX_ERROR = new SyntaxErrorDebuggerEvaluationResult();
    public final static InvokeMtfDebuggerEvaluationResult INVOKE_MTF_DEBUGGER_EVALUATION_RESULT = new InvokeMtfDebuggerEvaluationResult();

    public boolean hasSemanticError() {
        return false;
    }

    public boolean hasSyntaxError() {
        return false;
    }

    public boolean isInvokeMTF() {
        return false;
    }

    public boolean isNull() {
        return false;
    }

    public static DebuggerEvaluationResult fromValue(ITypeBinding binding, Value value) {
        if (binding.isPrimitive() && value != null) {
            if (PrimitiveType.toCode(binding.getName()) == PrimitiveType.INT && value != null && value instanceof IntegerValue)
                return new IntegerDebuggerEvaluationResult(((IntegerValue) value).value());
            else if (PrimitiveType.toCode(binding.getName()) == PrimitiveType.DOUBLE && value instanceof DoubleValue)
                return new DoubleDebuggerEvaluationResult(((DoubleValue) value).value());
            else if (PrimitiveType.toCode(binding.getName()) == PrimitiveType.LONG && value instanceof LongValue)
                return new LongDebuggerEvaluationResult(((LongValue) value).value());
            else if (PrimitiveType.toCode(binding.getName()) == PrimitiveType.FLOAT && value instanceof FloatValue)
                return new FloatDebuggerEvaluationResult(((FloatValue) value).value());
            else if (PrimitiveType.toCode(binding.getName()) == PrimitiveType.BOOLEAN && value instanceof BooleanValue)
                return getBooleanDebugValue(((BooleanValue) value).value());
            else if (PrimitiveType.toCode(binding.getName()) == PrimitiveType.CHAR && value instanceof BooleanValue)
                return getCharDebugValue(((CharValue) value).value());
        } else if (!binding.isPrimitive()) {
            if (value == null)
                return getReferenceDebuggerEvaluationResultNull();
            else if (value instanceof ObjectReference)
                return new ReferenceDebuggerEvaluationResult(((ObjectReference) value).uniqueID(), value.toString(), ((ObjectReference) value).referenceType().name());
        } else {
            throw new IllegalStateException("Unexpected value type [" + value.type().name() + "].");
        }
        return getDebuggerEvaluationResultSemanticError();

    }

    public static DebuggerEvaluationResult fromValue(Value value) {
        if (value != null) {
            if (value instanceof IntegerValue)
                return new IntegerDebuggerEvaluationResult(((IntegerValue) value).value());
            else if (value instanceof DoubleValue)
                return new DoubleDebuggerEvaluationResult(((DoubleValue) value).value());
            else if (value instanceof LongValue)
                return new LongDebuggerEvaluationResult(((LongValue) value).value());
            else if (value instanceof FloatValue)
                return new FloatDebuggerEvaluationResult(((FloatValue) value).value());
            else if (value instanceof BooleanValue)
                return getBooleanDebugValue(((BooleanValue) value).value());
            else if (value instanceof CharValue)
                return getCharDebugValue(((CharValue) value).value());
            else if (value instanceof ObjectReference)
                return new ReferenceDebuggerEvaluationResult(((ObjectReference) value).uniqueID(), value.toString(), ((ObjectReference) value).referenceType().name());
            else
                return getDebuggerEvaluationResultSemanticError();
        } else {
            return getReferenceDebuggerEvaluationResultNull();
        }
    }

    public static SemanticErrorDebuggerEvaluationResult getDebuggerEvaluationResultSemanticError() {
        return DEBUGGER_EVALUATION_RESULT_SEMANTIC_ERROR;
    }

    public static SyntaxErrorDebuggerEvaluationResult getDebuggerEvaluationResultSyntaxError() {
        return DEBUGGER_EVALUATION_RESULT_SYNTAX_ERROR;
    }

    public static IntegerDebuggerEvaluationResult getIntegerDebugValue(int value) {
        return new IntegerDebuggerEvaluationResult(value);
    }

    public static CharDebuggerEvaluationResult getCharDebugValue(char value) {
        return new CharDebuggerEvaluationResult(value);
    }

    public static InvokeMtfDebuggerEvaluationResult getInvokeMtfDebuggerEvaluationResult() {
        return INVOKE_MTF_DEBUGGER_EVALUATION_RESULT;
    }

    public static BooleanDebuggerEvaluationResult getBooleanDebugValue(boolean value) {
        if (value)
            return BOOLEAN_DEBUGGER_EVALUATION_RESULT_TRUE;
        else
            return BOOLEAN_DEBUGGER_EVALUATION_RESULT_FALSE;
    }

    public static ReferenceDebuggerEvaluationResult getReferenceDebuggerEvaluationResultNull() {
        return REFERENCE_DEBUGGER_EVALUATION_RESULT_NULL;
    }

    public static class DoubleDebuggerEvaluationResult extends DebuggerEvaluationResult {
        private final double value;

        private DoubleDebuggerEvaluationResult(double value) {
            this.value = value;
        }

        public double getValue() {
            return value;
        }

        public boolean isGreater(DoubleDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() > other.getValue();
        }

        public boolean isGreaterEqual(DoubleDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() >= other.getValue();
        }

        public boolean isLess(DoubleDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() < other.getValue();
        }

        public boolean isLessEqual(DoubleDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() <= other.getValue();
        }

        @Override
        public String toString() {
            return "Double{" +
                    "value=" + value +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DoubleDebuggerEvaluationResult that = (DoubleDebuggerEvaluationResult) o;
//            return getValue() == that.getValue();
            return new Double(getValue()).equals(that.getValue());
        }

        @Override
        public int hashCode() {
            long temp = Double.doubleToLongBits(getValue());
            return (int) (temp ^ (temp >>> 32));
        }
    }

    public static class LongDebuggerEvaluationResult extends DebuggerEvaluationResult {
        private final long value;

        private LongDebuggerEvaluationResult(long value) {
            this.value = value;
        }

        public long getValue() {
            return value;
        }

        public boolean isGreater(LongDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() > other.getValue();
        }

        public boolean isGreaterEqual(LongDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() >= other.getValue();
        }

        public boolean isLess(LongDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() < other.getValue();
        }

        public boolean isLessEqual(LongDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() <= other.getValue();
        }

        @Override
        public String toString() {
            return "Long{" +
                    "value=" + value +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LongDebuggerEvaluationResult that = (LongDebuggerEvaluationResult) o;

            return getValue() == that.getValue();
        }

        @Override
        public int hashCode() {
            long temp = getValue();
            return (int) (temp ^ (temp >>> 32));
        }
    }

    public static class FloatDebuggerEvaluationResult extends DebuggerEvaluationResult {
        private final float value;

        private FloatDebuggerEvaluationResult(float value) {
            this.value = value;
        }

        public float getValue() {
            return value;
        }

        public boolean isGreater(FloatDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() > other.getValue();
        }

        public boolean isGreaterEqual(FloatDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() >= other.getValue();
        }

        public boolean isLess(FloatDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() < other.getValue();
        }

        public boolean isLessEqual(FloatDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() <= other.getValue();
        }

        @Override
        public String toString() {
            return "Float{" +
                    "value=" + value +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FloatDebuggerEvaluationResult that = (FloatDebuggerEvaluationResult) o;

            return getValue() == that.getValue();
        }

        @Override
        public int hashCode() {
            long temp = Float.floatToIntBits(getValue());
            return (int) (temp ^ (temp >>> 32));
        }
    }

    public static class IntegerDebuggerEvaluationResult extends DebuggerEvaluationResult {
        private final int value;

        private IntegerDebuggerEvaluationResult(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public boolean isGreater(IntegerDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() > other.getValue();
        }

        public boolean isGreaterEqual(IntegerDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() >= other.getValue();
        }

        public boolean isLess(IntegerDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() < other.getValue();
        }

        public boolean isLessEqual(IntegerDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() <= other.getValue();
        }

        @Override
        public String toString() {
            return "Integer{" +
                    "value=" + value +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            IntegerDebuggerEvaluationResult that = (IntegerDebuggerEvaluationResult) o;

            return getValue() == that.getValue();
        }

        @Override
        public int hashCode() {
            return getValue();
        }
    }

    public static class CharDebuggerEvaluationResult extends DebuggerEvaluationResult {
        private final char value;

        private CharDebuggerEvaluationResult(char value) {
            this.value = value;
        }

        public char getValue() {
            return value;
        }

        public boolean isGreater(CharDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() > other.getValue();
        }

        public boolean isGreaterEqual(CharDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() >= other.getValue();
        }

        public boolean isLess(CharDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() < other.getValue();
        }

        public boolean isLessEqual(CharDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() <= other.getValue();
        }

        @Override
        public String toString() {
            return "Integer{" +
                    "value=" + value +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CharDebuggerEvaluationResult that = (CharDebuggerEvaluationResult) o;

            return getValue() == that.getValue();
        }

        @Override
        public int hashCode() {
            return getValue();
        }
    }

    public static class BooleanDebuggerEvaluationResult extends DebuggerEvaluationResult {
        private final boolean value;

        private BooleanDebuggerEvaluationResult(boolean value) {
            this.value = value;
        }

        public boolean getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "Boolean{" +
                    "value=" + value +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BooleanDebuggerEvaluationResult that = (BooleanDebuggerEvaluationResult) o;

            return getValue() == that.getValue();
        }

        @Override
        public int hashCode() {
            return (getValue() ? 1 : 0);
        }
    }

    public static class ReferenceDebuggerEvaluationResult extends DebuggerEvaluationResult {
        private final long objectID;
        private final String objectToString;
        private final String referenceType;

        //        private ReferenceDebuggerEvaluationResult(long objectID) {
//            this.objectID = objectID;
//        }
        private ReferenceDebuggerEvaluationResult(long objectID, String objectToString, String referenceType) {
            if (objectToString.startsWith("\"") && objectToString.endsWith("\""))
                objectToString = objectToString.substring(1, objectToString.length() - 1);
            this.objectID = objectID;
            this.objectToString = objectToString;
            this.referenceType = referenceType;
        }

        public long getObjectID() {
            return objectID;
        }

        public String getObjectToString() {
            return objectToString;
        }

        public String getReferenceType() {
            return referenceType;
        }

        @Override
        public boolean isNull() {
            return getObjectID() == 0;
        }

        @Override
        public String toString() {
            return "Reference{" +
//                    "objectID=" + objectID +
                    "objectToString=" + objectToString +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ReferenceDebuggerEvaluationResult that = (ReferenceDebuggerEvaluationResult) o;

            return
//                    getObjectID() == that.getObjectID() &&
//                    getObjectToString().equals(that.getObjectToString()) &&
                    getReferenceType().equals(that.getReferenceType());
        }

        @Override
        public int hashCode() {
//            int result = (int) (getObjectID() ^ (getObjectID() >>> 32));
//            result = 31 * result + getReferenceType().hashCode();
//            result = 31 * result + getObjectToString().hashCode();
//            return result;
            return getReferenceType().hashCode();
        }

        public static final int NULL_REFERENCE_ID = 0;
        public static final String NULL_REFERENCE_STRING = "null";
        public static final String NULL_REFERENCE_TYPE = "NULL";
    }

    public static class SemanticErrorDebuggerEvaluationResult extends DebuggerEvaluationResult {

        private SemanticErrorDebuggerEvaluationResult() {
        }

        @Override
        public String toString() {
            return "SemanticError{}";
        }

        @Override
        public boolean hasSemanticError() {
            return true;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return false;
        }
    }

    public static class SyntaxErrorDebuggerEvaluationResult extends DebuggerEvaluationResult {

        private SyntaxErrorDebuggerEvaluationResult() {
        }

        @Override
        public String toString() {
            return "SyntaxError{}";
        }

        @Override
        public boolean hasSyntaxError() {
            return true;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return false;
        }
    }

    public static class InvokeMtfDebuggerEvaluationResult extends DebuggerEvaluationResult {

        private InvokeMtfDebuggerEvaluationResult() {
        }

        @Override
        public String toString() {
            return "InvokeMtf{}";
        }

        @Override
        public boolean isInvokeMTF() {
            return true;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return false;
        }
    }

}
