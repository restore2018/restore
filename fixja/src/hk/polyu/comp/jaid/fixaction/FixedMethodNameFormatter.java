package hk.polyu.comp.jaid.fixaction;

/**
 * Created by Max PEI.
 */
public class FixedMethodNameFormatter {

    public static final String FIX_ID_PREFIX = "__";
    public static final String DISPATCHER_ID_PREFIX = "_";

    public FixedMethodNameFormatter(){
    }

    public String getDispatcherName(String originalMethodName, int dispatcherID){
        StringBuilder sb = new StringBuilder(originalMethodName);
        sb.append(DISPATCHER_ID_PREFIX).append(dispatcherID);
        return sb.toString();
    }

    public String getFixedMethodName(String originalMethodName, int index){
        StringBuilder sb = new StringBuilder(originalMethodName);
        sb.append(FIX_ID_PREFIX).append(index);
        return sb.toString();
    }

    public int getFixActionIndex(String fixedMethodName, String originalMethodName){
        int fixIndex = INVALID_INDEX;

        int idBegin = fixedMethodName.lastIndexOf(FIX_ID_PREFIX);
        if(idBegin < 0)
            return fixIndex;

        if(fixedMethodName.substring(0, idBegin).equals(originalMethodName)){
            fixIndex = Integer.parseInt(fixedMethodName.substring(idBegin + 2, fixedMethodName.length()));
        }

        return fixIndex;
    }

    public String getOriginalMethodName(String fixedMethodName){
        int idBegin = fixedMethodName.lastIndexOf(FIX_ID_PREFIX);
        return fixedMethodName.substring(0, idBegin);
    }

    public static final int INVALID_INDEX = -1;

}
