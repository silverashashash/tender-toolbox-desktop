package local.tender.tools.initialreport;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class InitialReportFormattingTest {
 @Test void suffixesAreLongestFirstRepeatedAndOnlyAtEnd(){
  var suffixes=InitialReportFormatting.suffixes(InitialReportFormatting.DEFAULT_SUFFIXES);
  assertEquals("示例",InitialReportFormatting.shortName("示例集团股份有限公司",suffixes));
  assertEquals("集团研究院",InitialReportFormatting.shortName("集团研究院",suffixes));
  assertEquals("有限公司",InitialReportFormatting.shortName("有限公司",suffixes));
 }
 @Test void mergesNumericRangesWithoutExpandingAndKeepsGaps(){
  assertEquals("包1至包3，包5，包10至包12",InitialReportFormatting.packs(List.of("包12至包10","包2,包1","包3、包5","包2")));
  assertEquals("包1至包999999999999",InitialReportFormatting.packs(List.of("包1至包999999999999")));
  assertThrows(IllegalArgumentException.class,()->InitialReportFormatting.packs(List.of("包待定")));
 }
 @Test void collidingAbbreviationsKeepFullIdentity(){
  var names=InitialReportFormatting.names(List.of("示例有限公司","示例集团有限公司"),InitialReportFormatting.suffixes(InitialReportFormatting.DEFAULT_SUFFIXES));
  assertEquals("示例有限公司",names.get("示例有限公司"));assertEquals("示例集团有限公司",names.get("示例集团有限公司"));
 }
}
