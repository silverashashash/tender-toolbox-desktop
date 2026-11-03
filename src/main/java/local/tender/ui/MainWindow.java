package local.tender.ui;
import local.tender.core.*;
import local.tender.io.WorkbookTable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public final class MainWindow extends JFrame {
 private InitialReportOptions initialReportOptions;
 private JTabbedPane priceTabs;
 private final DefaultComboBoxModel<String> priceMethodModel=new DefaultComboBoxModel<>(local.tender.tools.pricescore.PriceScoreTool.METHODS.toArray(String[]::new));
 private final ToolRegistry registry;
 private final TaskRunner runner;
 private final JList<ToolRegistry.Entry> navigation;
 private final JPanel detail=new JPanel(new BorderLayout(12,12));
 private final JTextArea log=new JTextArea();
 private final Map<ToolId,ToolForm.State> states=new EnumMap<>(ToolId.class);
 private final Map<String,JComboBox<String>> parameterChoices=new LinkedHashMap<>();
 private final Map<String,JTextField> inputFields=new LinkedHashMap<>(),parameterFields=new LinkedHashMap<>();
 private final List<JComponent> controls=new ArrayList<>();
 private final JTextField output=new JTextField();
 private final JButton run=new JButton("开始处理"),cancel=new JButton("取消任务");
 private final ChooserHistory history=new ChooserHistory(Path.of(System.getProperty("user.home"),".tender-toolbox","settings.properties"),Path.of(System.getProperty("user.home")));
 private Map<local.tender.tools.objective.ObjectiveTool.Selection,String> objectiveSelection=new LinkedHashMap<>();
 private boolean auxiliaryBusy;private ToolForm form;private ToolForm.State state;private TaskRunner.TaskHandle active;
 public MainWindow(ToolRegistry registry){
  super("招标现场工具箱 · Mac 开发验证版");this.registry=registry;
  runner=new TaskRunner(Path.of(System.getProperty("java.io.tmpdir"),"TenderToolbox","tasks"));
  setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);setSize(1120,800);setMinimumSize(new Dimension(900,650));setLocationRelativeTo(null);
  var root=new JPanel(new BorderLayout(20,16));root.setBorder(new EmptyBorder(20,20,20,20));setContentPane(root);
  var heading=new JLabel("招标现场工具箱");heading.setFont(heading.getFont().deriveFont(Font.BOLD,24));var top=new JPanel(new BorderLayout());top.add(heading,BorderLayout.WEST);top.add(new JLabel("本机处理 · 无需登录 · 无网站服务"),BorderLayout.EAST);root.add(top,BorderLayout.NORTH);
  navigation=new JList<>(registry.catalog().toArray(ToolRegistry.Entry[]::new));navigation.setFixedCellHeight(38);navigation.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);navigation.setBorder(new EmptyBorder(8,8,8,8));
  var nav=new JScrollPane(navigation);nav.setPreferredSize(new Dimension(245,0));root.add(nav,BorderLayout.WEST);root.add(detail,BorderLayout.CENTER);
  navigation.addListSelectionListener(e->{if(!e.getValueIsAdjusting())showTool();});run.addActionListener(e->execute());cancel.addActionListener(e->{if(active!=null){active.cancel();cancel.setEnabled(false);log.append("\n正在取消，请等待结束。\n");}});
  addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){if(active!=null||auxiliaryBusy){error("请先取消任务并等待结束，再关闭窗口。");return;}runner.close();dispose();}});
  navigation.setSelectedIndex(0);
 }
 private void showTool(){
  priceTabs=null;detail.removeAll();inputFields.clear();parameterFields.clear();parameterChoices.clear();controls.clear();var selected=navigation.getSelectedValue();if(selected==null)return;
  var title=new JLabel(selected.name());title.setFont(title.getFont().deriveFont(Font.BOLD,21));detail.add(title,BorderLayout.NORTH);
  if(registry.find(selected.id()) instanceof Outcome.Failure<?>){detail.add(new JLabel("此功能正在迁移，尚未开放运行。"),BorderLayout.CENTER);detail.revalidate();detail.repaint();return;}
  form=ToolForm.forTool(selected.id());state=states.computeIfAbsent(selected.id(),id->new ToolForm.State(form,Path.of(System.getProperty("user.home"),"Documents","招标工具箱结果")));
  var center=new JPanel(new BorderLayout(8,14));var fields=new JPanel(new GridLayout(0,1,0,8));
  if(form.id()==ToolId.PRICE_SCORE){
   var method=new JComboBox<>(priceMethodModel);controls.add(method);
   var line=new JPanel(new BorderLayout(8,0));var label=new JLabel("模板使用公式");label.setPreferredSize(new Dimension(145,30));line.add(label,BorderLayout.WEST);line.add(method,BorderLayout.CENTER);fields.add(line);
   method.setToolTipText("导出参数模板时自动填写所选公式；计算仍按参数表中的配置执行");
  }
  for(var input:form.inputs()){
   String hint=form.id()==ToolId.EXPERT_TOTAL&&input.key().equals("scores")?"技术/商务评分统计表（专家维度）":form.id()==ToolId.IP_MAC&&input.key().equals("files")?"投标文件IP地址一览表.xlsx":form.id()==ToolId.EXPERT_OBJECTIVE&&input.key().equals("files")?"技术/商务评审打分表按组.xlsx":null;
   JTextField field=hint==null?new JTextField(display(state.paths(input.key()))):new PlaceholderTextField(display(state.paths(input.key())),hint);field.setEditable(!input.multiple());inputFields.put(input.key(),field);controls.add(field);
   var choose=new JButton(input.multiple()?"选择文件（可多选）":"选择");choose.addActionListener(e->selectFiles(input));controls.add(choose);fields.add(row(input.label(),field,choose));
  }
  if(form.id()==ToolId.EXPERT_OBJECTIVE){
   var select=new JButton("读取评分项并勾选");select.addActionListener(e->selectObjectiveItems());controls.add(select);
   var line=new JPanel(new FlowLayout(FlowLayout.LEFT));line.add(select);line.add(new JLabel("先选择评分明细；勾选后自动生成规则，无需手填"));fields.add(line);
  }
  for(var parameter:form.parameters()){
   if(form.id()==ToolId.AWARD&&parameter.key().equals("mode")){
    var choice=new JComboBox<>(local.tender.tools.award.AwardTool.MODES.toArray(String[]::new));choice.setSelectedItem(state.parameter("mode"));parameterChoices.put("mode",choice);controls.add(choice);
    var line=new JPanel(new BorderLayout(8,0));var label=new JLabel("授标方式");label.setPreferredSize(new Dimension(145,30));line.add(label,BorderLayout.WEST);line.add(choice,BorderLayout.CENTER);fields.add(line);
    choice.addActionListener(e->{state.parameter("mode",(String)choice.getSelectedItem());log.setText(AwardHelp.text(state.parameter("mode")));log.setCaretPosition(0);});continue;
   }
   JTextField field=parameter.secret()?new JPasswordField():parameter.key().equals("allowedTaxes")?new PlaceholderTextField(state.parameter(parameter.key()),"默认：服务0,1,3,5,6,9,13；物资13。可填逗号分隔数字，清单外提示税率异常"):new JTextField(state.parameter(parameter.key()));parameterFields.put(parameter.key(),field);controls.add(field);fields.add(row(parameter.label(),field,null));
  }
  if(form.id()==ToolId.INITIAL_REPORT){
   if(initialReportOptions==null)initialReportOptions=new InitialReportOptions(Path.of(System.getProperty("user.home"),".tender-toolbox","initial-report-names.properties"));
   var options=initialReportOptions;var line=new JPanel(new FlowLayout(FlowLayout.LEFT));line.add(options.enabled);line.add(new JLabel("仅影响输出名称；取消勾选保留全称"));fields.add(line);
   fields.add(row("删除后缀（逗号分隔）",options.suffixes,options.save));controls.add(options.enabled);controls.add(options.suffixes);controls.add(options.save);
  }
  output.setText(state.output().toString());controls.add(output);var chooseOutput=new JButton("选择");chooseOutput.addActionListener(e->{var chooser=chooser(true);if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){Path path=chooser.getSelectedFile().toPath();state.output(path);output.setText(path.toString());remember(path,true);}});controls.add(chooseOutput);fields.add(row("结果保存目录",output,chooseOutput));
  var actions=new JPanel(new FlowLayout(FlowLayout.LEFT));actions.add(run);controls.add(run);actions.add(cancel);cancel.setEnabled(false);
  if(form.inputs().stream().anyMatch(i->!i.templateHeaders().isEmpty())){var templates=new JButton("导出填写模板");templates.setToolTipText("保存到当前结果保存目录");templates.addActionListener(e->templates());actions.add(templates);controls.add(templates);}
  var demo=new JButton("载入演示样例");demo.addActionListener(e->demo());actions.add(demo);controls.add(demo);
  var open=new JButton("打开结果目录");open.addActionListener(e->{try{Path directory=Path.of(output.getText());if(Files.isDirectory(directory))Desktop.getDesktop().open(directory.toFile());else error("结果目录尚不存在。");}catch(Exception ex){error(ex.getMessage());}});actions.add(open);controls.add(open);fields.add(actions);
  center.add(fields,BorderLayout.NORTH);log.setEditable(false);log.setLineWrap(true);log.setWrapStyleWord(true);log.setText(form.id()==ToolId.AWARD?AwardHelp.text(state.parameter("mode")):form.help());if(form.id()==ToolId.PRICE_SCORE){
   var tabs=new JTabbedPane();tabs.addTab("公式说明",new PriceFormulaPanel(priceMethodModel));tabs.addTab("本批配置",priceConfigurationPanel());tabs.addTab("使用说明 / 运行结果",new JScrollPane(log));
   priceTabs=tabs;
   center.add(tabs,BorderLayout.CENTER);
  }else center.add(new JScrollPane(log),BorderLayout.CENTER);detail.add(center,BorderLayout.CENTER);detail.revalidate();detail.repaint();
 }
 private JPanel priceConfigurationPanel(){
  var panel=new JPanel(new BorderLayout(8,8));panel.setBorder(new EmptyBorder(10,10,10,10));
  var model=new javax.swing.table.DefaultTableModel(new String[]{"分标名称","计算公式","价格权重"},0){public boolean isCellEditable(int r,int c){return false;}};
  var table=new JTable(model);table.setRowHeight(28);table.setFillsViewportHeight(true);panel.add(new JScrollPane(table),BorderLayout.CENTER);
  var read=new JButton("读取当前参数表");var status=new JLabel("选择参数表后点击读取；修改文件后请重新读取。");
  var top=new JPanel(new BorderLayout(10,0));top.add(read,BorderLayout.WEST);top.add(status,BorderLayout.CENTER);panel.add(top,BorderLayout.NORTH);controls.add(read);
  read.addActionListener(e->{
   String path=inputFields.get("rules").getText().trim();if(path.isEmpty()){error("请先选择价格公式参数表。");return;}
   model.setRowCount(0);auxiliaryBusy=true;setBusy(true);cancel.setEnabled(false);status.setText("正在读取并校验参数……");
   UiBackground.run(()->local.tender.tools.pricescore.PriceScoreTool.configurations(Path.of(path)),rows->{
    auxiliaryBusy=false;setBusy(false);for(var row:rows)model.addRow(new Object[]{row.section(),row.method(),row.weight()});status.setText("已读取 "+rows.size()+" 个分标；仅为本次读取快照");
   },ex->{auxiliaryBusy=false;setBusy(false);status.setText("读取失败，请检查参数表");error(ex.getMessage());});
  });return panel;
 }
 private JPanel row(String title,JTextField field,JButton choose){var row=new JPanel(new BorderLayout(8,0));var label=new JLabel(title);label.setPreferredSize(new Dimension(145,30));row.add(label,BorderLayout.WEST);row.add(field,BorderLayout.CENTER);if(choose!=null)row.add(choose,BorderLayout.EAST);return row;}
 private String display(List<Path> paths){return String.join(" ; ",paths.stream().map(Path::toString).toList());}
 private JFileChooser chooser(boolean directory){var chooser=new JFileChooser();Path recent=history.directory();if(recent!=null)chooser.setCurrentDirectory(recent.toFile());chooser.setFileSelectionMode(directory?JFileChooser.DIRECTORIES_ONLY:JFileChooser.FILES_ONLY);return chooser;}
 private void remember(Path path,boolean directory){try{history.remember(path,directory);}catch(Exception e){log.append("\n本次选择有效，但无法保存目录历史："+e.getMessage()+"\n");}}
 private void selectFiles(ToolForm.Input input){
  var chooser=chooser(false);chooser.setMultiSelectionEnabled(input.multiple());if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
  List<Path> paths=input.multiple()?Arrays.stream(chooser.getSelectedFiles()).map(f->f.toPath().toAbsolutePath().normalize()).toList():List.of(chooser.getSelectedFile().toPath().toAbsolutePath().normalize());
  if(paths.isEmpty())return;
  if(form.id()==ToolId.EXPERT_OBJECTIVE&&(input.key().equals("files")||input.key().equals("rules"))){objectiveSelection.clear();if(input.key().equals("files")){state.clearInput("rules");inputFields.get("rules").setText("");}}
  state.select(input.key(),paths);inputFields.get(input.key()).setText(display(paths));output.setText(state.output().toString());remember(paths.get(paths.size()-1),false);
 }
 private void selectObjectiveItems(){
  var files=state.paths("files");if(files.isEmpty()){error("请先选择专家评分明细表。");return;}
  auxiliaryBusy=true;setBusy(true);cancel.setEnabled(false);log.setText("正在读取评分细则……");
  UiBackground.run(()->local.tender.tools.objective.ObjectiveTool.discoverMethods(files),items->{
   auxiliaryBusy=false;setBusy(false);var selected=ObjectiveSelectionDialog.choose(this,items,objectiveSelection);
   if(selected==null){log.setText("已取消勾选，原规则保持不变。");return;}
   auxiliaryBusy=true;setBusy(true);cancel.setEnabled(false);
   UiBackground.run(()->{
    Path folder=Files.createTempDirectory("TenderToolbox-objective-rules-");Path file=folder.resolve("已勾选客观项规则.xlsx");
    WorkbookTable.write(file,"客观项规则",local.tender.tools.objective.ObjectiveTool.SCOPED_RULE_HEADERS,selected.entrySet().stream().map(e->List.of(e.getKey().criterion(),e.getValue(),e.getKey().methodId())).toList());return file;
   },file->{
    auxiliaryBusy=false;setBusy(false);Path destination=state.output();state.select("rules",List.of(file));state.output(destination);inputFields.get("rules").setText(file.toString());objectiveSelection=new LinkedHashMap<>(selected);
    log.setText("已勾选 "+selected.size()+" 项客观评分细则：\n"+String.join("\n",selected.keySet().stream().map(k->"评分办法"+(java.util.stream.IntStream.range(0,items.size()).filter(i->items.get(i).id().equals(k.methodId())).findFirst().orElse(0)+1)+"："+k.criterion()).toList())+"\n\n点击“开始处理”核验；可再次点击“读取评分项并勾选”调整。");
   },e->{auxiliaryBusy=false;setBusy(false);error("生成勾选规则失败："+e.getMessage());});
  },e->{auxiliaryBusy=false;setBusy(false);error("读取评分项失败："+e.getMessage());});
 }
 private void demo(){
  auxiliaryBusy=true;setBusy(true);cancel.setEnabled(false);
  var selectedForm=form;
  UiBackground.run(()->ToolDemos.create(selectedForm.id(),Path.of(System.getProperty("java.io.tmpdir"),"TenderToolbox-demo")),demo->{
  auxiliaryBusy=false;setBusy(false);
  if(form.id()==ToolId.EXPERT_OBJECTIVE)objectiveSelection.clear();
  for(var input:form.inputs()){state.select(input.key(),demo.inputs().getOrDefault(input.key(),List.of()));inputFields.get(input.key()).setText(display(state.paths(input.key())));}
  for(var parameter:form.parameters()){String value=demo.parameters().getOrDefault(parameter.key(),parameter.initial());if(parameterChoices.containsKey(parameter.key()))parameterChoices.get(parameter.key()).setSelectedItem(value);else parameterFields.get(parameter.key()).setText(value);}
  state.output(demo.output());output.setText(state.output().toString());log.setText("已载入虚构样例。\n预期："+demo.expected()+"\n\n点击“开始处理”验证。实际业务请重新选择输入文件及长期保存目录。");
 },e->{auxiliaryBusy=false;setBusy(false);error("演示样例创建失败："+e.getMessage());});}
 private void execute(){try{
  boolean customOutput=!output.getText().equals(state.output().toString());
  for(var input:form.inputs())if(!input.multiple()){
   String text=inputFields.get(input.key()).getText();if(text.isBlank()&&input.optional()){state.clearInput(input.key());continue;}if(text.isBlank())throw new IllegalArgumentException("请选择："+input.label());Path path=Path.of(text);
   if(!state.paths(input.key()).equals(List.of(path))){state.select(input.key(),List.of(path));if(!customOutput)output.setText(state.output().toString());}
  }
  if(output.getText().isBlank())throw new IllegalArgumentException("请选择结果保存目录");state.output(Path.of(output.getText()));
  for(var parameter:form.parameters()){
   if(parameterChoices.containsKey(parameter.key())){state.parameter(parameter.key(),(String)parameterChoices.get(parameter.key()).getSelectedItem());continue;}
   JTextField field=parameterFields.get(parameter.key());if(field instanceof JPasswordField secret){char[] chars=secret.getPassword();state.parameter(parameter.key(),new String(chars));Arrays.fill(chars,'\0');secret.setText("");}else state.parameter(parameter.key(),field.getText());
  }
  boolean overwrite=false;if(Files.exists(state.output().resolve(form.outputName()))){if(JOptionPane.showConfirmDialog(this,"结果文件已存在，是否覆盖？","确认覆盖",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION){clearSecrets();return;}overwrite=true;}
  if(form.id()==ToolId.INITIAL_REPORT)initialReportOptions.apply(state);
  if(priceTabs!=null)priceTabs.setSelectedIndex(2);
  ToolRequest request=state.request(overwrite);clearSecrets();var handler=(Outcome.Success<ToolHandler>)registry.find(form.id());setBusy(true);log.setText((form.id()==ToolId.AWARD?AwardHelp.text(state.parameter("mode"))+"\n\n":"")+"正在处理……\n");
  active=runner.submit(handler.value(),request,p->SwingUtilities.invokeLater(()->log.append(p.stage()+"\n")));
  active.result().whenComplete((result,error)->SwingUtilities.invokeLater(()->{
   active=null;setBusy(false);if(error!=null){log.append("未完成："+error.getMessage());return;}
   if(result instanceof Outcome.Success<ToolResult> success){for(Path path:success.value().outputs())log.append("已保存："+path+"\n");for(var warning:success.warnings())log.append("提示："+warning.message()+"\n");}
   else if(result instanceof Outcome.Failure<ToolResult> failure)for(var problem:failure.problems())log.append("未完成："+problem.message()+"\n");
  }));
 }catch(Exception e){clearSecrets();active=null;setBusy(false);error(e.getMessage());}}
 private void clearSecrets(){if(form!=null)for(var p:form.parameters())if(p.secret())state.parameter(p.key(),"");}
 private void setBusy(boolean busy){navigation.setEnabled(!busy);for(var control:controls)control.setEnabled(!busy);cancel.setEnabled(busy);}
 private void templates(){
  final Path folder;try{if(output.getText().isBlank())throw new IllegalArgumentException("请先填写结果保存目录，模板会保存到该目录。");folder=Path.of(output.getText()).toAbsolutePath();}catch(Exception e){error(e.getMessage());return;}
  var selectedForm=form;String selectedPriceMethod=(String)priceMethodModel.getSelectedItem();String selectedAwardMode=state.parameter("mode");auxiliaryBusy=true;setBusy(true);cancel.setEnabled(false);log.setText("正在导出填写模板……\n保存目录："+folder);
  UiBackground.run(()->TemplateExport.save(selectedForm,folder,selectedPriceMethod,selectedAwardMode),saved->{auxiliaryBusy=false;setBusy(false);
   String message="已导出 "+saved.size()+" 个模板：\n"+String.join("\n",saved.stream().map(Path::toString).toList());log.setText(message+(selectedForm.id()==ToolId.AWARD?"\n\n"+AwardHelp.text(selectedAwardMode):""));log.setCaretPosition(0);
   if(JOptionPane.showOptionDialog(this,message,"模板导出成功",JOptionPane.DEFAULT_OPTION,JOptionPane.INFORMATION_MESSAGE,null,new String[]{"打开所在文件夹","关闭"},"打开所在文件夹")==0){try{Desktop.getDesktop().open(folder.toFile());}catch(Exception e){error("文件已导出，但无法打开目录："+folder);}}
  },e->{auxiliaryBusy=false;setBusy(false);error("模板导出未完成："+e.getMessage()+"\n请检查目录："+folder);});
 }
 private void error(String message){JOptionPane.showMessageDialog(this,message,"提示",JOptionPane.WARNING_MESSAGE);}
}
