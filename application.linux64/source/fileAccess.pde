// functionality for loading from and saving to files
Table table;

void openStuff(){
  try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) { e.printStackTrace(); }
  fileChooser.updateUI();
  JDialog fileWrapper = new JDialog(); fileWrapper.setVisible(false); fileWrapper.setAlwaysOnTop(true);
  int result = fileChooser.showOpenDialog(fileWrapper);
  if(result != JFileChooser.APPROVE_OPTION){return;}
  String selectedFile = fileChooser.getSelectedFile().getAbsolutePath();
  println(selectedFile);
  if(selectedFile.toLowerCase().endsWith(".png")) { // makes the background image
    MainFrame.bgi = loadImage(selectedFile);
    MainFrame.fx = float(MainFrame.lower.base.width)/MainFrame.bgi.width; MainFrame.fy = float(MainFrame.lower.base.height)/MainFrame.bgi.height;
    MainFrame.bgi.resize(MainFrame.lower.base.width, MainFrame.lower.base.height);
  }
  else if(selectedFile.toLowerCase().endsWith(".bundle")){ loadBundle(selectedFile); MainFrame.TIME.reset(); }
  else if(selectedFile.toLowerCase().endsWith(".setting")){ loadSetting(selectedFile); check_setting(selectedFile); }
  else if(selectedFile.toLowerCase().endsWith(".mp4")){ MainFrame.clip = new Movie(this, selectedFile); }
  else if(selectedFile.toLowerCase().endsWith(".csv")){ attach(selectedFile); MainFrame.process(); MainFrame.TIME.reset();}
  // ... other valid input files to consider?
  println("done opening", selectedFile);
}
void saveStuff(){
  try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) { e.printStackTrace(); }
  fileChooser.updateUI();
  JDialog fileWrapper = new JDialog(); fileWrapper.setVisible(false); fileWrapper.setAlwaysOnTop(true);
  int result = fileChooser.showSaveDialog(fileChooser);
  if(result != JFileChooser.APPROVE_OPTION){return;}
  String selectedFile = fileChooser.getSelectedFile().getAbsolutePath();
  if(selectedFile.toLowerCase().endsWith(".bundle")) { saveBundle(selectedFile); } // saves the bundling
  else if(selectedFile.toLowerCase().endsWith(".png")) { save(selectedFile); } // takes a screenshot
  else if(selectedFile.toLowerCase().endsWith(".setting")) { saveSetting(selectedFile); } // saves visualisation settings information
  else{ // save everything, manually adding the file types
    saveBundle(selectedFile+".bundle");
    save(selectedFile+".png");
    MainFrame.base.save(selectedFile+"_frame.png");
    MainFrame.lower.base.save(selectedFile+"_base.png");
    MainFrame.bgi.save(selectedFile+"_background.png");
    saveSetting(selectedFile+".setting"); check_setting(selectedFile); 
    export_timeline(selectedFile+"_timeline.csv");
  }
}

void check_setting(String filename){
  if(filename.endsWith(".setting")){filename = filename.substring(0, filename.lastIndexOf('.'));} // strip off the filetype, will handle ourselves
  for(int i=0;i<controller.screens.size();i++){ // if we already know this setting, update image and go home
    String s = controller.screen_sources.get(i);
    if(filename==s){
      try{
        PImage c = loadImage(filename + ".png");
        c.resize(150, 100);
        controller.screens.set(i, c);
      }catch(Exception e){
        controller.screens.remove(i); controller.screen_sources.remove(i); // we found him, but the png is no longer real, so delete?
      }
      return;
    }
  }
  // otherwise, add a new guy
  try{
    PImage c = loadImage(filename + ".png");
    c.resize(150, 100);
    controller.screens.add(c);
    controller.screen_sources.add(filename); // so we can find them again
  }catch(Exception e){}
}

void attach(String name){
  table = loadTable(name, "header");
  int M = table.getRowCount();
  MainFrame.nodes = new ArrayList<Node>(M+1);
  if(M==0){return;} // no data present, and next line would throw an error
  MainFrame.TIME.TTmin = table.getRow(0).getFloat(0); MainFrame.TIME.TTmax = table.getRow(0).getFloat(0);
  for(int i=0;i<M;i++){
    TableRow r1 = table.getRow(i);
    float type = 1; if(r1.getColumnCount()>4){type = r1.getFloat(4);}
    Node current = new Node(r1.getFloat(0), r1.getFloat(1)*MainFrame.fx, r1.getFloat(2)*MainFrame.fy, r1.getFloat(3), type, MainFrame );
    if(r1.getFloat(0)<MainFrame.TIME.TTmin){MainFrame.TIME.TTmin = r1.getFloat(0);}
    if(r1.getFloat(0)>MainFrame.TIME.TTmax){MainFrame.TIME.TTmax = r1.getFloat(0);}
    if(MainFrame.nodes.size()>0){
      current.last_node(MainFrame.nodes.get(MainFrame.nodes.size()-1));
      MainFrame.nodes.get(MainFrame.nodes.size()-1).next_node(current);
    }
    MainFrame.nodes.add(current);
  }
  MainFrame.nodes_backup = (ArrayList<Node>) MainFrame.nodes.clone();
}

void saveBundle(String selectedFile){
  PrintWriter writer;
  try{ writer = new PrintWriter(selectedFile, "UTF-8"); }
  catch (Exception e) { e.printStackTrace(); return; }
  writer.println("x,y,l,t,dt");
  // x, y, l, t, dt (last two zero except at locked nodes?)
  for(NodeCurve n : MainFrame.node_curves){
    printrow(writer, n.x1, n.y1, 1, n.t, n.dt, n.type);
    for(int i=0; i<n.xs.length;i++){
      printrow(writer, n.xs[i], n.ys[i], 0,0,0,0);
    }
  }
  Node n = MainFrame.nodes.get(MainFrame.nodes.size()-1); // final node to close the bundle
  printrow(writer, n.x, n.y, 1, n.t, n.dt, n.type);
  writer.close();
  println("Writer finished writing", writer, selectedFile);
}
void printrow(PrintWriter w, float x, float y, float l, float t, float dt, float type){
  for(float i : new float[] {x,y,l,t,dt}){w.print(i);w.print(',');}
  w.print(type);w.print('\n');
}
void loadBundle(String bundlePath){
  table = loadTable(bundlePath, "header, csv");
  int M = table.getRowCount();
  if(M==0){return;} // don't bother with empty tables
  MainFrame.nodes = new ArrayList<Node>();
  MainFrame.node_curves = new ArrayList<NodeCurve>();
  MainFrame.TIME.TTmin = table.getRow(0).getFloat(3); MainFrame.TIME.TTmax = table.getRow(0).getFloat(3); // begin setting up time bounds
  for(int i=0;i<M;){
    TableRow r1 = table.getRow(i);
    float x = r1.getFloat(0), y=r1.getFloat(1), t = r1.getFloat(3), dt=r1.getFloat(4);
    float type = 1; if(r1.getColumnCount()>5){type = r1.getFloat(5);}
    int m = 0;
    while( i+m+1 < M && (m==0 || table.getRow(i+m+1).getFloat(2)==0) ){m++;}
    float[] xs=new float[m], ys = new float[m];
    for(int j=0; j<m; j++){
      xs[j] = table.getRow(i+j+1).getFloat(0); ys[j] = table.getRow(i+j+1).getFloat(1);
    }
    Node c1 = new Node( t,x,y, dt, type, MainFrame );
    if(r1.getFloat(3)<MainFrame.TIME.TTmin){MainFrame.TIME.TTmin = r1.getFloat(3);}
    if(r1.getFloat(3)>MainFrame.TIME.TTmax){MainFrame.TIME.TTmax = r1.getFloat(3);}
    if(m>0){
      NodeCurve c2 = new NodeCurve( x,y,t, table.getRow(i+m+1).getFloat(3) - t - dt,xs,ys, type, MainFrame );
      if(MainFrame.nodes.size()>0){
        c1.last_node(MainFrame.nodes.get(MainFrame.nodes.size()-1));
        MainFrame.nodes.get(MainFrame.nodes.size()-1).next_node(c1);
        MainFrame.node_curves.get(MainFrame.node_curves.size()-1).next(c2);
        c2.prev(MainFrame.node_curves.get(MainFrame.node_curves.size()-1));
      }
      MainFrame.node_curves.add(c2);
    }
    MainFrame.nodes.add(c1);
    i+=m+1;
  }
  MainFrame.TIME.Tmin = MainFrame.TIME.TTmin; MainFrame.TIME.Tmax = MainFrame.TIME.TTmax; MainFrame.TIME.Ts = 0; MainFrame.TIME.Te=1;
  for(NodeCurve n : MainFrame.node_curves){n.make_content();} // Glance status is known at this point in time
}



void saveSetting(String selectedFile){
  PrintWriter writer;
  try{ writer = new PrintWriter(selectedFile, "UTF-8"); }catch(Exception e) { return; }
  // first row : global boolean parameters
  boolean[] basic_bools = new boolean[] {SHOW_SHORT,SHOW_GLANCE,SHOW_BASIC,GENERAL,SHOW_DOTS,THIST,LHIST,BUNDLE,CLIP_MODE,USE_SIZE,ANIMATE,ALTERNATE,INTERLACE,TRAVEL_LINES};
  for(boolean b : basic_bools){writer.print(b);writer.print(',');}
  writer.println();
  // second row, global float parameters
  float[] basic_nums = new float[] {ALT_TIME_WINDOW, BRIEF_MIN, z, R, BIG_K, MainFrame.TIME.Ts, MainFrame.TIME.Te, MainFrame.TIME.Tmin, MainFrame.TIME.Tmax, SIZE};
  for(float b : basic_nums){writer.print(b);writer.print(',');}
  writer.println();
  // third row, global int parameters
  int[] basic_ints = new int[] {MainFrame.FILTER.mode, COL_MODE};
  for(int b : basic_ints){writer.print(b);writer.print(',');}
  writer.println();
  // fourth row, time markers. first is length
  writer.print(MainFrame.TIME.markers.size());writer.print(',');for(float b :MainFrame.TIME.markers){writer.print(b);writer.print(',');}
  writer.println();
  // fifth row, number of lenses, other filter parameters
  writer.print(MainFrame.FILTER.Lenses.size());writer.print(',');
  int[] filter_props = new int[] {MainFrame.FILTER.Lenses.size(), MainFrame.FILTER.selected};
  for(int b : filter_props){writer.print(b);writer.print(',');}
  writer.println();
  // sixth row, the lenses themselves. blocks of 5 floats
  for(Lens ls : MainFrame.FILTER.Lenses){for(float i : new float[] {ls.WINDOW, ls.FACTOR, ls.LS, ls.mX, ls.mY}){writer.print(i);writer.print(',');}}
  writer.println();
  // seventh row, does the notes
  MainFrame.notes.write(writer); writer.println();
  writer.close();
  println("Writer finished writing", writer, selectedFile);
}
void loadSetting(String settingPath){
  int l; // various lengths, will use a bit later
  Table set = loadTable(settingPath, "csv");
  // first row : global boolean parameters
  TableRow r1 = set.getRow(0);
  SHOW_SHORT = r1.getString(0).equals("true");
  SHOW_GLANCE = r1.getString(1).equals("true");
  SHOW_BASIC = r1.getString(2).equals("true");
  GENERAL = r1.getString(3).equals("true");
  SHOW_DOTS = r1.getString(4).equals("true");
  THIST = r1.getString(5).equals("true");
  LHIST = r1.getString(6).equals("true");
  BUNDLE = r1.getString(7).equals("true");
  CLIP_MODE = r1.getString(8).equals("true");
  USE_SIZE = r1.getString(9).equals("true");
  ANIMATE = r1.getString(10).equals("true");
  ALTERNATE = r1.getString(11).equals("true");
  INTERLACE = r1.getString(12).equals("true");
  TRAVEL_LINES = r1.getString(13).equals("true");
  // second row, global numeric parameters
  r1 = set.getRow(1); println(r1.toString());
  ALT_TIME_WINDOW = r1.getFloat(0);
  BRIEF_MIN = r1.getFloat(1);
  z = r1.getFloat(2);
  R = r1.getFloat(3);
  BIG_K = r1.getFloat(4);
  MainFrame.TIME.Ts = r1.getFloat(5);
  MainFrame.TIME.Te = r1.getFloat(6);
  MainFrame.TIME.Tmin = r1.getFloat(7);
  MainFrame.TIME.Tmax = r1.getFloat(8);
  SIZE = r1.getFloat(9);
  // third row, basic Int parameters
  r1 = set.getRow(2); println(r1.toString());
  MainFrame.FILTER.mode = r1.getInt(0); COL_MODE = r1.getInt(1);
  // fourth row, time markers length
  r1 = set.getRow(3);
  l = r1.getInt(0);
  MainFrame.TIME.markers = new ArrayList<Float>(); for(int i=0;i<l;i++){MainFrame.TIME.markers.add(r1.getFloat(i+1));}
  // fifth row, number of lenses, other filter parameters
  r1 = set.getRow(4); 
  l = r1.getInt(0); MainFrame.FILTER.selected = r1.getInt(1);
  // sixth row, the lenses themselves. blocks of 5 floats
  r1 = set.getRow(5);
  MainFrame.FILTER.Lenses = new ArrayList<Lens>();
  for(int i=0;i<l;i++){
    Lens newlen = new Lens(MainFrame);
    newlen.WINDOW = r1.getFloat(5*i+0); newlen.FACTOR = r1.getFloat(5*i+1); newlen.LS = r1.getFloat(5*i+2);
    newlen.update(r1.getFloat(5*i+3), r1.getFloat(5*i+4));
    MainFrame.FILTER.Lenses.add(newlen);
  }
  MainFrame.FILTER.selected = min(MainFrame.FILTER.selected, MainFrame.FILTER.Lenses.size()-1); // sanity check
  // do updates based on new filtering, knowledge of COL_MODE
  MainFrame.FILTER.update_all();
  for(NodeCurve n : MainFrame.node_curves){n.apply_color();}
  // seventh row, does the notes (can be after updating since doesn't affect anything)
  MainFrame.notes.read(set.getRow(6));
}

void export_timeline(String selectedFile){
  PrintWriter writer;
  try{ writer = new PrintWriter(selectedFile, "UTF-8"); }catch(Exception e) { return; }
  writer.print("t,x,y,dt,");
  for(int i=0;i<MainFrame.FILTER.Lenses.size();i++){writer.print(i);writer.print(',');}
  writer.println();
  for(Node n : MainFrame.nodes){
    for(float a: new float[] {n.t, n.x, n.y, n.dt} ){writer.print(a);writer.print(',');}
    for(Lens l : MainFrame.FILTER.Lenses){writer.print(l.inside(n.x, n.y));writer.print(',');}
    writer.println();
  }
  writer.close();
  println("Writer finished writing", writer, selectedFile);
}
