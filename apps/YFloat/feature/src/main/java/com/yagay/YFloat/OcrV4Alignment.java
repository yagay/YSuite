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
        // Dynamic edit-distance alignment exposes shifts/insertions rather
        // than hiding them behind a coincidentally identical string length.
        // A non-whitespace insertion/deletion is never approved for copying.
        int n=symbols.size(),m=cp.size();
        int[][] distance=new int[n+1][m+1];
        for(int i=0;i<=n;i++)distance[i][0]=i;
        for(int j=0;j<=m;j++)distance[0][j]=j;
        for(int i=1;i<=n;i++)for(int j=1;j<=m;j++){
            int sub=Character.toLowerCase(symbols.get(i-1))==
                    Character.toLowerCase(cp.get(j-1))?0:1;
            distance[i][j]=Math.min(distance[i-1][j-1]+sub,
                    Math.min(distance[i-1][j]+1,distance[i][j-1]+1));
        }
        int[] mapping=new int[n];
        java.util.Arrays.fill(mapping,-1);
        int i=n,j=m,gaps=0;
        while(i>0||j>0){
            int sub=(i>0&&j>0&&
                    Character.toLowerCase(symbols.get(i-1))==
                    Character.toLowerCase(cp.get(j-1)))?0:1;
            if(i>0&&j>0&&distance[i][j]==distance[i-1][j-1]+sub){
                mapping[i-1]=j-1;i--;j--;
            }else if(i>0&&distance[i][j]==distance[i-1][j]+1){
                i--;gaps++;
            }else{j--;gaps++;}
        }
        int[] starts=new int[mlSymbols.size()],ends=new int[mlSymbols.size()];
        for(int k=0;k<starts.length;k++){starts[k]=-1;ends[k]=-1;}
        int matched=0;
        for(int k=0;k<n;k++){
            int sourcePos=mapping[k];
            if(sourcePos<0)continue;
            int id=symbolOwner.get(k);
            if(Character.toLowerCase(cp.get(sourcePos))==
                    Character.toLowerCase(symbols.get(k)))matched++;
            if(starts[id]<0)starts[id]=from.get(sourcePos);
            ends[id]=to.get(sourcePos);
        }
        boolean complete=true;
        for(int k=0;k<starts.length;k++)if(starts[k]<0||ends[k]<starts[k])
            complete=false;
        // Alignment confidence is a TEXTUAL agreement check (not merely
        // matching the number of symbols), with substitutions bounded.
        // Keep all final characters exclusively from Paddle.
        boolean reliable=complete && gaps==0 && matched>=Math.max(1,
                (int)Math.ceil(cp.size()*0.80d));
        return new Result(source,starts,ends,reliable,matched,
                cp.size()-matched);
    }
}
