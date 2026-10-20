package com.yagay.YFloat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Restore geometrically orphaned Paddle glyphs belonging to ONE unbroken
 * original ASCII word already anchored to exactly ONE ML Kit Element.
 *
 * This does not perform OCR, invent characters, infer brand names, change
 * Paddle output, or use ML Kit's recognized text as clipboard content.
 * It only prevents independently detected "W" or "h" glyph rectangles from
 * disappearing from an otherwise mapped "WhatsApp"-shaped source token.
 *
 * Plain Java deliberately makes the exact ownership rules unit-testable.
 */
final class OcrV3LatinTokenRecovery {
    static final class Span {
        final int line, from, to;
        Span(int line,int from,int to){
            this.line=line;this.from=from;this.to=to;
        }
    }
    static final class Result {
        final int[] owners;
        final int recovered,unmatched;
        Result(int[] owners,int recovered,int unmatched){
            this.owners=owners;this.recovered=recovered;this.unmatched=unmatched;
        }
    }
    private static final class Group {
        final ArrayList<Integer> glyphs=new ArrayList<>();
        int owner=-1;
        boolean ambiguous;
        int anchors;
    }
    private OcrV3LatinTokenRecovery(){}

    private static boolean latin(char c){
        return c>='a'&&c<='z' || c>='A'&&c<='Z' || c>='0'&&c<='9';
    }

    /** Only actual source text offsets, not geometric distance or ML text. */
    private static String tokenKey(List<String> raw,Span s){
        if(s.line<0||s.line>=raw.size())return null;
        String line=raw.get(s.line);
        if(s.from<0||s.to>s.from&&s.to>line.length()||
                s.to<=s.from||s.from>=line.length())return null;
        for(int j=s.from;j<s.to;j++)if(!latin(line.charAt(j)))return null;
        int start=s.from,end=s.to;
        while(start>0&&latin(line.charAt(start-1)))start--;
        while(end<line.length()&&latin(line.charAt(end)))end++;
        if(end-start<2)return null;
        return s.line+":"+start+":"+end;
    }

    static Result recover(List<String> lines,List<Span> glyphs,int[] initialOwners){
        if(glyphs==null||initialOwners==null||
                glyphs.size()!=initialOwners.length)
            throw new IllegalArgumentException("glyph assignments");
        int[] owners=initialOwners.clone();
        Map<String,Group> groups=new HashMap<>();
        for(int i=0;i<glyphs.size();i++){
            String token=tokenKey(lines,glyphs.get(i));
            if(token==null)continue;
            Group group=groups.computeIfAbsent(token,k->new Group());
            group.glyphs.add(i);
            if(owners[i]<0)continue;
            group.anchors++;
            if(group.owner<0)group.owner=owners[i];
            else if(group.owner!=owners[i])group.ambiguous=true;
        }
        int recovered=0;
        for(Group group:groups.values()){
            // Require repeated geometry anchors and consensus. Never attach a
            // token split across two ML Elements or with only a single anchor.
            if(group.ambiguous||group.owner<0||group.anchors<2||
                    group.anchors*2<group.glyphs.size())continue;
            for(int id:group.glyphs){
                if(owners[id]<0){owners[id]=group.owner;recovered++;}
            }
        }
        int unmatched=0;
        for(int id:owners)if(id<0)unmatched++;
        return new Result(owners,recovered,unmatched);
    }
}
