package local.tender.tools.initialreport;
import java.util.*;import java.math.BigInteger;import java.util.regex.Pattern;
public final class InitialReportFormatting {
 public static final String DEFAULT_SUFFIXES="有限责任公司,有限公司,公司,集团,股份有限责任公司,园区,股份有限公司";
 private InitialReportFormatting(){}
 public static List<String> suffixes(String value){return Arrays.stream(value.split("[,，、\\r\\n]",-1)).map(String::trim).filter(s->!s.isEmpty()).distinct().sorted(Comparator.comparingInt(String::length).reversed()).toList();}
 public static String shortName(String full,List<String> suffixes){
  String value=full.trim();boolean changed;
  do{changed=false;for(String suffix:suffixes)if(!suffix.isEmpty()&&value.endsWith(suffix)){
   String next=value.substring(0,value.length()-suffix.length()).trim();if(next.isEmpty())return full;
   value=next;changed=true;break;
  }}while(changed);return value;
 }
 public static Map<String,String> names(Collection<String> fullNames,List<String> suffixes){
  var result=new LinkedHashMap<String,String>();var counts=new HashMap<String,Integer>();
  for(String full:new LinkedHashSet<>(fullNames)){String shortName=shortName(full,suffixes);result.put(full,shortName);counts.merge(shortName,1,Integer::sum);}
  result.replaceAll((full,shortName)->counts.get(shortName)>1?full:shortName);return result;
 }
 private record Interval(BigInteger start,BigInteger end){}
 private static final Pattern PACK=Pattern.compile("包?\\s*(\\d+)\\s*(?:至\\s*包?\\s*(\\d+))?");
 public static String packs(Collection<String> values){
  var intervals=new ArrayList<Interval>();
  for(String value:values)for(String token:value.split("[,，、]",-1)){
   var match=PACK.matcher(token.trim());if(!match.matches())throw new IllegalArgumentException("无法识别包号："+value+"；请使用包1、包1至包3或逗号分隔的包号");
   BigInteger a=new BigInteger(match.group(1)),b=match.group(2)==null?a:new BigInteger(match.group(2));
   if(a.signum()<=0||b.signum()<=0)throw new IllegalArgumentException("包号须为正整数："+value);
   intervals.add(new Interval(a.min(b),a.max(b)));
  }
  intervals.sort(Comparator.comparing(Interval::start).thenComparing(Interval::end));var merged=new ArrayList<Interval>();
  for(var next:intervals){if(merged.isEmpty()){merged.add(next);continue;}var previous=merged.get(merged.size()-1);
   if(next.start.compareTo(previous.end.add(BigInteger.ONE))<=0)merged.set(merged.size()-1,new Interval(previous.start,previous.end.max(next.end)));else merged.add(next);
  }
  return String.join("，",merged.stream().map(r->r.start.equals(r.end)?"包"+r.start:"包"+r.start+"至包"+r.end).toList());
 }
}
