package local.tender.ui;
import local.tender.tools.pricescore.PriceScoreTool;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Read-only formula reference; never changes calculation parameters. */
final class PriceFormulaPanel extends JPanel {
 private static final String[] STEPS={
  "有效报价 → 按数量去极值 → 均价T → 区间筛选 → 基准价B",
  "有效报价 → 均价A → 下浮 → 基准价B",
  "有效报价 → 均价A → 区间筛选 → 浮动 → 双边曲线评分",
  "有效报价 → 均价A → 区间筛选 → 复合基准价B",
  "有效报价 → 均价A → 剔除低报价 → 浮动 → 基准价B",
  "有效报价 → 按家数分支 → 区间筛选 → 浮动 → 基准价B"
 };
 private static final String[] BASE={
  "≤5家不去极值；6–10家去1高1低；11–20家去2高1低；21–30家去3高2低；>30家去4高3低。<br>按条数剔除，并列报价不额外剔除。剩余均价为T。<br>在剩余报价中保留 [(1+w1)×T, (1+w2)×T]，B=保留报价均值×(1−c)。区间为空时用T。",
  "A=全部有效报价的算术平均值；B=A×(1−c)。",
  "在 [F1×A, F2×A] 内筛选，B=保留报价均值×(1+c)。区间为空时用A。",
  "在 [(1+b)×A, (1+c)×A] 内筛选，B=保留报价均值。<br>区间为空且最低价唯一时，B=(A+最低价)÷2；最低价并列时提示口径待确认。",
  "保留 P≥A×(1−m) 的报价，B=保留报价均值×(1+c)。",
  "≤5家：B=A×(1+c)。<br>>5家：保留 (1+a)×A < P < (1+b)×A，B=保留报价均值×(1+c)；区间为空时用A。"
 };
 private static final String[] PARAMETERS={
  "w1：区间下界系数，−1<w1<0；w2：上界系数，0<w2<1。<br>c：下浮系数，−0.1<c<0.1；n1/n2：高价/低价扣分系数，均>0。",
  "c：下浮系数，−0.1<c<0.1；n1/n2：高价/低价扣分系数，均>0。",
  "F1：下界倍数，0<F1<1；F2：上界倍数，1<F2<2。<br>c：浮动系数，−0.1<c<0.1；n1/n2：高价/低价曲线指数，均>0。",
  "b：下界系数，−1<b<0；c：上界系数，0<c<1（本公式中c不是下浮系数）。<br>n1/n2：高价/低价扣分系数，均>0。",
  "m：低价剔除系数，0≤m<1；c：浮动系数，−0.1<c<0.1。<br>F1/F2：高价/低价扣分系数，均>0。",
  "a：下界系数，−1<a<0；b：上界系数，0<b<1。<br>c：浮动系数，−0.1<c<0.1；n1/n2：高价/低价扣分系数，均>0。"
 };
 PriceFormulaPanel(ComboBoxModel<String> selection){
  super(new BorderLayout(8,8));setBorder(new EmptyBorder(10,10,10,10));
  var choice=new JComboBox<>(selection);
  var top=new JPanel(new BorderLayout(8,5));top.add(new JLabel("查看公式说明"),BorderLayout.WEST);top.add(choice,BorderLayout.CENTER);
  var north=new JPanel(new BorderLayout(0,7));north.add(top,BorderLayout.NORTH);
  var note=new JLabel("与模板公式选择同步；计算按参数表执行。qz填30表示30%，不要填0.3。");note.setForeground(new Color(90,90,90));north.add(note,BorderLayout.SOUTH);add(north,BorderLayout.NORTH);
  var text=new JEditorPane("text/html","");text.setEditable(false);text.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES,true);text.setFont(UIManager.getFont("Label.font").deriveFont(14f));
  var scroll=new JScrollPane(text);scroll.setBorder(BorderFactory.createEmptyBorder());add(scroll,BorderLayout.CENTER);
  Runnable update=()->{text.setText(description(choice.getSelectedIndex()));text.setCaretPosition(0);};choice.addActionListener(e->update.run());update.run();
 }
 private static String escape(String value){return value.replaceAll("<(?!/?(?:br|sup)>)","&lt;");}
 private static String description(int i){
  String score=i==2?"P≥B：价格分=100×(B/P)<sup>n1</sup>；P<B：价格分=100×(P/B)<sup>n2</sup>。":
   "价格分=max(0, 100−100×k×|P−B|÷B)。<br>P≥B时k取"+(i==4?"F1":"n1")+"，P<B时k取"+(i==4?"F2":"n2")+"。";
  String example=i==2?"B=100、P=110、n1=1、qz=30：价格分90.91，加权价格分27.27。":"B=100、P=110、高价扣分系数=1、qz=30：价格分90，加权价格分27。";
  return "<html><body style='margin:8px;color:#253247'><h2>"+(i+1)+" · "+PriceScoreTool.METHODS.get(i)+"</h2><p style='color:#285e8e'>"+STEPS[i]+"</p><p>P：当前供应商报价　A：有效报价均值　B：基准价</p><h3>基准价怎样计算</h3><p>"+escape(BASE[i])+"</p><h3>价格分怎样计算</h3><p>"+escape(score)+"</p><p><b>加权价格分=价格分÷100×qz</b>，价格分和加权价格分均保留两位小数。</p><h3>参数怎样填写</h3><p>"+escape(PARAMETERS[i])+"<br>qz：价格权重，0至100，例如30表示30%。其余系数按小数填写，例如−0.15表示−15%。</p><h3>评分示例（假设基准价已算出）</h3><p>"+example+"</p></body></html>";
 }
}
