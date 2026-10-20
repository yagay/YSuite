package com.yagay.YFloat;

/**
 * Conservative source-only text decision for recognition of one selected
 * ML Element. ML decoded words NEVER enter the text values here; only their
 * symbol count can indicate missing Paddle source coverage.
 */
final class OcrV3LocalTextDecision {
    private OcrV3LocalTextDecision(){}
    static boolean needsRecognition(int mlSymbolCharacters,String cached){
        if(mlSymbolCharacters<=0)return cached==null||cached.isEmpty();
        int source=cached==null?0:cached.length();
        return source==0 || (mlSymbolCharacters>=3 && source<mlSymbolCharacters);
    }
    static String choose(String cached,String candidate,float confidence){
        String original=cached==null?"":cached;
        if(candidate==null||candidate.isBlank()||
                !Float.isFinite(confidence)||confidence<0.45f)
            return original;
        if(original.isEmpty())return candidate;
        // The local crop may recover the beginning/end of one original word.
        // Never replace source with a shorter or unrelated transcription.
        if(candidate.length()>original.length() &&
                candidate.length()-original.length()<=Math.max(4,original.length()/2) &&
                (candidate.startsWith(original)||candidate.endsWith(original)) &&
                (!original.chars().noneMatch(Character::isWhitespace) ||
                        candidate.chars().noneMatch(Character::isWhitespace)))
            return candidate;
        if(candidate.equals(original))return original;
        return original;
    }
}
