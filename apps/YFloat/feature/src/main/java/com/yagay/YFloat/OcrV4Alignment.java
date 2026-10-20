package com.yagay.YFloat;

import java.util.ArrayList;
import java.util.List;

/**
 * One immutable Paddle source line aligned to ML Kit visual symbol positions.
 *
 * The ML transcription is used ONLY to verify symbol-to-source offsets.
 * Every emitted substring comes directly from the original Paddle line.
 * Unlike positional length heuristics, missing/inserted characters fail
 * rather than silently shifting the rest of the selection.
 */
final class OcrV4Alignment {
    static final class Result {
        final String paddle;
        final int[] starts,ends;
        final boolean reliable;
        final int matches,mismatches;
        Result(String paddle,int[] starts,int[] ends,boolean ok,int matches,int mismatches){
            this.paddle=paddle;this.starts=starts;this.ends=ends;
            reliable=ok;this.matches=matches;this.mismatches=mismatches;
        }
        String slice(int first,int last){
            if(!reliable||first<0||last<first||last>=starts.length)return "";
            return paddle.substring(starts[first],ends[last]);
        }
        int size(){return starts.length;}
    }

    private OcrV4Alignment(){}

    /** Spaces between ML Elements are not Symbol units. */
    static Result align(List<String> mlSymbols,String paddle){
        String source=paddle==null?"":paddle;
        if(mlSymbols==null||mlSymbols.isEmpty()||source.isEmpty())
            return new Result(source,new int[0],new int[0],false,0,0);
        ArrayList<Integer> cp=new ArrayList<>(),from=new ArrayList<>(),to=new ArrayList<>();
        for(int u=0;u<source.length();){
            int c=source.codePointAt(u),next=u+Character.charCount(c);
            if(!Character.isWhitespace(c)&&!Character.isISOControl(c)){
                cp.add(c);from.add(u);to.add(next);
            }
            u=next;
        }
        ArrayList<Integer> symbols=new ArrayList<>();
        ArrayList<Integer> symbolOwner=new ArrayList<>();
        for(int i=0;i<mlSymbols.size();i++){
            String visual=mlSymbols.get(i);
            if(visual==null||visual.isEmpty())
                return new Result(source,new int[0],new int[0],false,0,0);
            for(int u=0;u<visual.length();){
                int c=visual.codePointAt(u);
                if(!Character.isWhitespace(c)&&!Character.isISOControl(c)){
                    symbols.add(c);symbolOwner.add(i);
                }
                u+=Character.charCount(c);
            }
        }
        // Never silently synthesize missing characters or manufacture a
        // 1:1 index from an unequal number of code points.
        if(cp.size()!=symbols.size()||cp.isEmpty())
            return new Result(source,new int[0],new int[0],false,0,
                    Math.abs(cp.size()-symbols.size()));
        int[] starts=new int[mlSymbols.size()],ends=new int[mlSymbols.size()];
        for(int i=0;i<starts.length;i++){starts[i]=-1;ends[i]=-1;}
        int matched=0;
        for(int i=0;i<cp.size();i++){
            int id=symbolOwner.get(i),actual=cp.get(i),visual=symbols.get(i);
            if(Character.toLowerCase(actual)==Character.toLowerCase(visual))
                matched++;
            if(starts[id]<0)starts[id]=from.get(i);
            ends[id]=to.get(i);
        }
        boolean complete=true;
        for(int i=0;i<starts.length;i++)if(starts[i]<0||ends[i]<starts[i])
            complete=false;
        // Alignment confidence is a TEXTUAL agreement check (not merely
        // matching the number of symbols), with substitutions bounded.
        // Keep all final characters exclusively from Paddle.
        boolean reliable=complete && matched>=Math.max(1,
                (int)Math.ceil(cp.size()*0.80d));
        return new Result(source,starts,ends,reliable,matched,
                cp.size()-matched);
    }
}
