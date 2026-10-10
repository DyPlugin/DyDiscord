package dev.dydiscord.update;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.*;

/** Semantic precedence, including the shipped uppercase BETA spelling. */
public record VersionNumber(List<BigInteger> core,List<String> prerelease) implements Comparable<VersionNumber> {
    private static final Pattern FORMAT=Pattern.compile("(?i)^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([a-z0-9][a-z0-9.-]*))?(?:\\+[a-z0-9][a-z0-9.-]*)?$");
    public static Optional<VersionNumber> parse(String text){
        if(text==null||text.length()>80)return Optional.empty();Matcher match=FORMAT.matcher(text);if(!match.matches())return Optional.empty();
        List<BigInteger> core=List.of(new BigInteger(match.group(1)),new BigInteger(match.group(2)),new BigInteger(match.group(3)));
        String pre=match.group(4);List<String> identifiers=pre==null?List.of():List.of(pre.toLowerCase(Locale.ROOT).replaceAll("([a-z])(\\d)","$1.$2").split("[.-]",-1));
        if(identifiers.stream().anyMatch(String::isEmpty))return Optional.empty();return Optional.of(new VersionNumber(core,identifiers));
    }
    @Override public int compareTo(VersionNumber other){
        for(int i=0;i<3;i++){int compare=core.get(i).compareTo(other.core.get(i));if(compare!=0)return compare;}
        if(prerelease.isEmpty()||other.prerelease.isEmpty())return prerelease.isEmpty()?(other.prerelease.isEmpty()?0:1):-1;
        for(int i=0;i<Math.min(prerelease.size(),other.prerelease.size());i++){
            String a=prerelease.get(i),b=other.prerelease.get(i);boolean an=a.matches("\\d+"),bn=b.matches("\\d+");
            int compare=an&&bn?new BigInteger(a).compareTo(new BigInteger(b)):an!=bn?(an?-1:1):a.compareTo(b);if(compare!=0)return compare;
        }return Integer.compare(prerelease.size(),other.prerelease.size());
    }
}
