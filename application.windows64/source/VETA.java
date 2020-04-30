import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import processing.video.*; 
import static org.jocl.CL.*; 
import static java.lang.System.*; 
import java.io.File; 
import javax.swing.*; 

import org.jocl.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class VETA extends PApplet {

// VETA 0.27



// file selection
 

JFileChooser fileChooser = new JFileChooser();

// some essential global objects
String src_cl, startmess; PImage blank;
Frame[] Frames = new Frame[4];
int selected_frame = 0; Boolean show_all = false;
Frame MainFrame;
ControlWindow controller;
// constant numerics, not user controllable
float PHI = (sqrt(5)-1)/2;
float TWIDTH = 1600, TBUFFER=50; // control placement of the timeline
float R = 0.3f; //reduced size inside the len
float GLANCE_STANDARD = 3; // how much closer the before and after must be, compared to the middle, to call it a glance
int MAX_LENSES = 10; // limit for the number of lenses allowed
// Side threads for bundling data, and updating edge acceptance
volatile boolean is_bundling = false, is_activating = false;

// user controls features
float SIZE = 10; // general factor for symbol sizes
boolean CLIP_MODE = false, KunTingMode = false; // toggles showing the video
boolean SHOW_SHORT = true, SHOW_GLANCE = false, SHOW_BASIC = false, SHOW_DOTS = true; // lower layer show/hide toggles
boolean GENERAL = true, ALTERNATE = false, USE_SIZE = true, ANIMATE = false;  // foreground modes, whether to use dt for sizing, whether to use animated saccades
boolean INTERLACE = false; // foreground alternate view interlaced lines option setting
boolean THIST = false, LHIST = false, PHIST=true; float swap_time=-2000; // shows the location-time pairing lines. ( original suggestion was a sliding animation, might do later )
boolean BUNDLE = false; // toggles continuous bundling
boolean TRAVEL_LINES = false; // draw the extra 'travel' lines between before and after nodes
float z=1; // degree of display zooming
float ALT_TIME_WINDOW = 10; // number of seconds to look around an observation in the alternative view
float BRIEF_MIN = 100; // length below which saccades are classed as short
int COL_MODE = 0; // 0 = no colouring, 1 = by type, 2 = by angle (effects bundling rules similarly)
float BIG_K = 0.05f; // 0.05 is I think an appropriate choice, until I next modify the other bundling parameters. But for more crowded screens, higher is needed.
boolean lmp = false, rmp = false; // (track whether left or right mouse is currently held down)
int dval = -1; int gval = -1; int aval = -1; // control the mouse-over-legend filtering functionality

//Colouring and other graphical features
public int c1(float a){return color(17,100, 80,a);} // glances - background
public int c2(float a){return color(50,100, 80,a);} // normal - background
public int c3(float a){return color(70,100,100,a);} // close - background
public int o1(float a){return color( 0, 60, 60,a);} // before - foreground
public int o2(float a){return color(17, 60,100,a);} // glances - foreground
public int o3(float a){return color(40,100, 80,a);} // after - foreground
public int cy(float a, float type){return color((50 + (type*100*PHI))%100, 100, 80, a);} // colours based on data-defined type value
public boolean wheel_test(float x){ return dval==-1 || (COL_MODE==2 && floor(4 + (8*x)/TWO_PI + 0.5f)%8==dval); }
public int color_wheel(float a, float x){ return color( ((floor(((PI+x)*8)/TWO_PI + 0.5f)) * 12.5f) % 100, 100, 80, a); }
public int white(float a){return color(0, 0,100, a);} // plain white
public int  grey(float a){return color(0, 0, 50, a);} // plain grey, used for out-of-filter time marks
public int black(float a){return color(0, 0,  0, a);} // plain black
int[] sws = new int[] {2,4,8,12},  divs = new int[] {1,6,10,14}; // used in the node_curve alpha edge splattering
PFont  f; PFont  fb; // the font used for general text writing applications. defined in setup

public void setup(){
  
  surface.setTitle("Viz");
  src_cl=join(loadStrings("forces.cl"),"\n"); startmess= String.join("\n", loadStrings("startmess.txt")); blank = loadImage("blank.png");
  for(int i=0;i<4;i++){Frames[i] = new Frame(i);}
  controller = new ControlWindow();
  colorMode(HSB, 100);
  f = createFont("Arial",12,true); fb = createFont("Arial",22,true);
  println("ready", sketchFile(""), height, width);
}
public void draw(){
  background(black(100)); //print(frameRate, ' ');
  MainFrame = Frames[selected_frame];
  check_mouse();
  if(!show_all){ Frames[selected_frame].draw(); image(MainFrame.base, 0, 0); if(GENERAL || ALTERNATE){Frames[selected_frame].FILTER.connectors();}}
  else{
    for(int i=0;i<4;i++){
      Frames[i].draw();
      PImage s = Frames[i].base.copy(); s.resize(s.width/2, s.height/2);
      image(s, s.width*(i%2), s.height*(i/2));
    }
  }
  // control and timeline components should be visible in any state
  MainFrame.FILTER.filtered_timeline();
  MainFrame.TIME.draw(); // draw the timelines
  controller.draw(); // should exist even in movie mode, since it has the button
}

public void check_mouse(){
  boolean changed = false;
  float cx = mouseX - width + 125, cy = mouseY-425;
  if(!controller.isOpen){changed = dval!=-1; dval=-1;
  //}else if(COL_MODE==1){
  //  float cx = mouseX - width + 125, cy = mouseY-425;
  //  if(min(cx,cy)<0 || max(cx,cy)<100){ changed=dval!=-1;dval=-1;}
  //  else{
  //    int r = floor((cy+25)/34);
  //    changed = dval!=r;dval = r;
  //  }
  }else if(COL_MODE==2 && abs(cx)<25 && abs(cy)<25){
    float x = atan2(cy, cx);
    int r = floor(4 + (8*x)/TWO_PI + 0.5f)%8;
    changed = dval!=r;dval = r;
  }else{ changed=dval!=-1;dval=-1;}
  
  if(controller.isOpen && abs(cx-50)<25 && abs(cy)<25){
    int r = 2+floor((cy+25)/17);
    if(GENERAL && (cx-50 < 0 || !ALTERNATE)){ changed|=gval!=r; gval = r; aval=0; }
    else                                    { changed|=aval!=r; aval = r; gval=0; }
  }else{ changed|= gval!=-1 || aval!=-1; gval = -1; aval=-1;}
  
  if(changed && gval+aval > -2){println("found:", gval, aval);}
  
  MainFrame.lower.has_changed |= changed;
}
class ControlWindow{
  boolean isOpen = true;
  int k1 = color(230,230,230,200), k2 = color(20,20,20,200); // done in RGB 256 since is defined before colorMode is
  PFont f1 = createFont("arial", 10, true);
  
  // reloading recently saved settings control functionality
  ArrayList<String> screen_sources = new ArrayList<String>();
  ArrayList<PImage> screens = new ArrayList<PImage>();
  int view_place = 0;
  float wx, wy;
  
  ControlWindow(){}
  public void draw(){
    noFill();stroke(white(100));
    if(!isOpen){ // just draw the tab and do nothing else
      rect(width-60, height-80, 40, 40);
      line(width-50, height-70, width-30, height-60);
      line(width-50, height-50, width-30, height-60);
      return;
    }
    //draw the tab in the open position
    rect(width-60, height-80, 40, 40);
    line(width-30, height-70, width-50, height-60);
    line(width-30, height-50, width-50, height-60);
    //draw the backing rectangle
    fill(black(30));noStroke();
    rect(width-150, 0, 150, height-100);
    wx = width - 150; wy = 0; textFont(f1);
    // draw the 3 type inclusion options
    if(SHOW_SHORT)  {fill(k1);rect(wx    , wy, 50, 50); fill(k2);text("short",  wx+  2,wy+25);}
    else{            fill(k2);rect(wx    , wy, 50, 50); fill(k1);text("short",  wx+  2,wy+25);}
    if(SHOW_GLANCE) {fill(k1);rect(wx+ 50, wy, 50, 50); fill(k2);text("glance", wx+ 52,wy+25);}
    else{            fill(k2);rect(wx+ 50, wy, 50, 50); fill(k1);text("glance", wx+ 52,wy+25);}
    if(SHOW_BASIC)  {fill(k1);rect(wx+100, wy, 50, 50); fill(k2);text("basic",  wx+102,wy+25);}
    else{            fill(k2);rect(wx+100, wy, 50, 50); fill(k1);text("basic",  wx+102,wy+25);}
    // draw General (G), Alternative (A), and Sizing (S)
    if(GENERAL)     {fill(k1);rect(wx    , wy+50, 50, 50); fill(k2);text("Generic",             wx+  2,wy+75);}
    else{            fill(k2);rect(wx    , wy+50, 50, 50); fill(k1);text("Generic",             wx+  2,wy+75);}
    if(ALTERNATE)   {fill(k1);rect(wx+ 50, wy+50, 50, 50); fill(k2);text("Alternate",           wx+ 52,wy+75);}
    else{            fill(k2);rect(wx+ 50, wy+50, 50, 50); fill(k1);text("Alternate",           wx+ 52,wy+75);}
    if(USE_SIZE)    {fill(k1);rect(wx+100, wy+50, 50, 50); fill(k2);text("Use Sizes",           wx+102,wy+75);}
    else{            fill(k2);rect(wx+100, wy+50, 50, 50); fill(k1);text("Use Sizes",           wx+102,wy+75);}
    // draw Background (D), Interlace (I), and Colour Purpose (X)
    if(SHOW_DOTS)   {fill(k1);rect(wx    ,wy+100, 50, 50); fill(k2);text("Show\nBack",          wx+  2,wy+115);}
    else{            fill(k2);rect(wx    ,wy+100, 50, 50); fill(k1);text("Hide\nBack",          wx+  2,wy+115);}
    if(INTERLACE)   {fill(k1);rect(wx+ 50,wy+100, 50, 50); fill(k2);text("Alternate\nPathlines",wx+ 52,wy+115);}
    else{            fill(k2);rect(wx+ 50,wy+100, 50, 50); fill(k1);text("Alternate\nPathlines",wx+ 52,wy+115);}
    if(CLIP_MODE)   {fill(k1);rect(wx+100,wy+100, 50, 50); fill(k2);text("Clip\nMode",          wx+102,wy+115);}
    else{            fill(k2);rect(wx+100,wy+100, 50, 50); fill(k1);text("Clip\nMode",          wx+102,wy+115);}
    // draw time pairing (B), clustering (V), and animation (O)
    if(THIST)          {fill(k1);rect(wx    ,wy+150, 25, 50); fill(k2);text("T\nHist",          wx+  2,wy+165);}
    else{               fill(k2);rect(wx    ,wy+150, 25, 50); fill(k1);text("T\nHist",          wx+  2,wy+165);}
    if(LHIST)          {fill(k1);rect(wx+ 25,wy+150, 25, 50); fill(k2);text("L\nHist",          wx+  27,wy+165);}
    else{               fill(k2);rect(wx+ 25,wy+150, 25, 50); fill(k1);text("L\nHist",          wx+  27,wy+165);}
    if(BUNDLE)         {fill(k1);rect(wx+ 50,wy+150, 50, 50); fill(k2);text("Cont.\nBundle",       wx+ 52,wy+165);}
    else if(!MainFrame.GPU_setup){fill(0 );rect(wx+ 50,wy+150, 50, 50); fill(0 );text("Cont.\nBundle",       wx+ 52,wy+165);} // grey out since it is disabled
    else{               fill(k2);rect(wx+ 50,wy+150, 50, 50); fill(k1);text("Cont.\nBundle",       wx+ 52,wy+165);}
                        fill(k2);rect(wx+100,wy+150, 50, 50); fill(k1);text("Clear\nBundle",       wx+102,wy+165); // no conditional, just have as black
    // last row, might not need it all
    if(MainFrame.notes.mode){fill(k1);rect(wx    ,wy+200, 50, 50); fill(k2);text("add notes",           wx+  2,wy+215);}
    else{                    fill(k2);rect(wx    ,wy+200, 50, 50); fill(k1);text("add notes",           wx+  2,wy+215);}
    if(TRAVEL_LINES)        {fill(k1);rect(wx+ 50,wy+200, 50, 50); fill(k2);text("Travel\nLines",       wx+ 52,wy+215);}
    else{                    fill(k2);rect(wx+ 50,wy+200, 50, 50); fill(k1);text("Travel\nLines",       wx+ 52,wy+215);}
    if(ANIMATE)             {fill(k1);rect(wx+100,wy+200, 50, 50); fill(k2);text("Animate",             wx+102,wy+215);}
    else{                    fill(k2);rect(wx+100,wy+200, 50, 50); fill(k1);text("Animate",             wx+102,wy+215);}
    
    // 1: COL_MODE, FILTER_MODE, ALT_TIME_WINDOW
    fill(k2);rect(wx    , wy+250, 50, 50); fill(k1);text("ColMode\n " + String.format("%d", COL_MODE),              wx+  2,wy+275);
    fill(k2);rect(wx+ 50, wy+250, 50, 50); fill(k1);text("FltrMode\n "+ String.format("%d", MainFrame.FILTER.mode), wx+ 52,wy+275);
    fill(k2);rect(wx+100, wy+250, 50, 50); fill(k1);text("Alt Time\n "+ String.format("%.0f", ALT_TIME_WINDOW),     wx+102,wy+275);
    // 2: Big_K, Brief_min
    fill(k2);rect(wx    , wy+300, 50, 50); fill(k1);text("K:\n "      + String.format("%.3f", BIG_K),          wx+  2,wy+325);
    fill(k2);rect(wx+ 50, wy+300, 50, 50); fill(k1);text("MinDist\n " + String.format("%.0f", BRIEF_MIN),      wx+ 52,wy+325);
    fill(k2);rect(wx+100, wy+300, 50, 50); fill(k1);text("Size\n " + String.format("%.0f", SIZE),              wx+102,wy+325);
    fill(k2);rect(wx    , wy+350, 50, 50); fill(k1);text("Reset\nRemoved",                                     wx+  2,wy+375); // no conditional, just have as black
    fill(k2);rect(wx+ 50, wy+350, 50, 50); fill(k1);text("Delete\nInside",                                     wx+ 52,wy+375); // no conditional, just have as black
    fill(k2);rect(wx+100, wy+350, 50, 50); fill(k1);text("Delete\nOutside",                                    wx+102,wy+375); // no conditional, just have as black
    
    if(COL_MODE==0){ // makes the legend, depending on the current colour mode
      strokeWeight(5); strokeCap(SQUARE);
      stroke(c3(100));line(wx+2, wy+405, wx+10, wy+405); text("Short",  wx+11, wy+408);
      stroke(c2(100));line(wx+2, wy+420, wx+10, wy+420); text("Long", wx+11, wy+423);
    }else if(COL_MODE==1){
      strokeWeight(5); strokeCap(SQUARE);
      stroke(c3(100));line(wx+2, wy+405, wx+10, wy+405); text("Short",  wx+11, wy+408);
      stroke(c1(100));line(wx+2, wy+420, wx+10, wy+420); text("Glance", wx+11, wy+423);
      stroke(c2(100));line(wx+2, wy+435, wx+10, wy+435); text("Normal", wx+11, wy+438);
    }else if(COL_MODE==2){
      strokeWeight(1);
      for(float i=0;i<1;i+=0.005f){
                             float alpha = 100;//if(dval>-1 && dval!=floor(i*8)){ alpha = 40; }
                             if(!wheel_test(atan2(sin(i*TWO_PI), cos(i*TWO_PI)))){ alpha = 40; }
                             stroke(color_wheel(alpha, PI + i*TWO_PI));
                             line(wx+25+10*cos(i*TWO_PI), wy+425+10*sin(i*TWO_PI), wx+25+20*cos(i*TWO_PI), wy+425+20*sin(i*TWO_PI));
      }
    }else if(COL_MODE==3){
      text("Custom\nColouring", wx+ 2, wy+420);
    }
    if(GENERAL || ALTERNATE){
      text("Glance", wx+53, wy+408);
      text("Before", wx+55, wy+423);
      text("After",  wx+60, wy+438);
      noStroke();
      if(GENERAL){
        if(gval==-1 || gval==2){ fill(o2(90)); ellipse(wx+50, wy+404, 6, 6);}
        if(gval==-1 || gval==3){ fill(o1(90)); ellipse(wx+50, wy+419, 6, 6);}
        if(gval==-1 || gval==4){ fill(o3(90)); ellipse(wx+50, wy+434, 6, 6);}
      }
      strokeWeight(2);
      if(ALTERNATE){
        if(aval==-1 || aval==2){ stroke(o2(90)); line(wx+90, wy+402, wx+96, wy+406);line(wx+96, wy+402, wx+90, wy+406);}
        if(aval==-1 || aval==3){ stroke(o1(90)); line(wx+90, wy+417, wx+96, wy+421);line(wx+96, wy+417, wx+90, wy+421);}
        if(aval==-1 || aval==4){ stroke(o3(90)); line(wx+90, wy+432, wx+96, wy+436);line(wx+96, wy+432, wx+90, wy+436);}
      }
    }
    strokeWeight(2); noStroke();
    if(show_all)            {fill(k1);rect(wx+100,wy+400, 50, 50); fill(k2);text("Show\nMultiple",    wx+102,wy+425);}
    else{                    fill(k2);rect(wx+100,wy+400, 50, 50); fill(k1);text("Show\nMultiple",    wx+102,wy+425);}
    //fill(k2);rect(wx+100, wy+50, 50, 50); fill(k1);text("Alt Time\n "+ String.format("%.0f", ALT_TIME_WINDOW),wx+102,wy+75);
    
    // settings menu, 3 guys tall
    wy = height - 150 - 300; // miniscreens will be 150px wide, 100px tall
    if(screens.size()>3){text(String.format("%d / %d", view_place, screens.size()), wx+10, wy-20);}
    for(int i=view_place;i < min(view_place+3, screens.size());i++){
      image(screens.get(i), wx, wy + 100*(i-view_place));
    }
    stroke(black(100));
    // Load and Save Buttons (at the very bottom)
    fill(k1);rect(width-150,height-150, 73, 50); fill(k2);text("Load", width-130,height-130);
    fill(k1);rect(width- 75,height-150, 73, 50); fill(k2);text("Save", width- 55,height-130);
  }
  
  public void clicked(){
    println("controls clicked", mouseX, mouseY);
    if(!isOpen || mouseY>height-100 || mouseX < width-150){return;} // shouldn't be on the ControlWindow 
    if(mouseY < 50){ // First Row
      if     (width-mouseX > 100){SHOW_SHORT ^=true;}
      else if(width-mouseX >  50){SHOW_GLANCE^=true;}
      else if(width-mouseX >   0){SHOW_BASIC ^=true;}
    }else if(mouseY < 100){ // Second Row
      if     (width-mouseX > 100){GENERAL^=true;}
      else if(width-mouseX >  50){ALTERNATE^=true;}
      else if(width-mouseX >   0){USE_SIZE^=true;}
    }else if(mouseY < 150){ // Third Row
      if     (width-mouseX > 100){SHOW_DOTS^=true;}
      else if(width-mouseX >  50){INTERLACE^=true;}
      else if(width-mouseX >   0){
        CLIP_MODE^=true;
        if(MainFrame.clip!=null){if(CLIP_MODE){MainFrame.clip.loop();}else{MainFrame.clip.pause();}}
        MainFrame.movie_start=millis();
      }
    }else if(mouseY < 200){ // Fourth Row
      if     (width-mouseX > 125){THIST^=true; LHIST=false; swap_time=millis(); PHIST=true;}
      else if(width-mouseX > 100){LHIST^=true; THIST=false; swap_time=millis(); PHIST=false;}
      else if(width-mouseX >  50){BUNDLE^=MainFrame.GPU_setup;}
      else if(width-mouseX >   0){MainFrame.process();}
    }else if(mouseY < 250){ // Fifth Row, probably last
      if     (width-mouseX > 100){MainFrame.notes.mode^=true;}
      else if(width-mouseX >  50){TRAVEL_LINES^=true;}
      else if(width-mouseX >   0){ANIMATE^=true;}
    }else if(mouseY < 300){
      if     (width-mouseX > 100){COL_MODE = (COL_MODE+1)%4; for(NodeCurve n : MainFrame.node_curves){n.apply_color();} }
      else if(width-mouseX >  50){MainFrame.FILTER.mode = (MainFrame.FILTER.mode+1)%5;}
      else if(width-mouseX >   0){ALT_TIME_WINDOW = (ALT_TIME_WINDOW+2)%60;}
    }else if(mouseY < 350){ 
      if     (width-mouseX > 100){}
      else if(width-mouseX >  50){}
      else if(width-mouseX >   0){SIZE = (SIZE+10)%100;}
    }else if(mouseY < 400){ 
      if     (width-mouseX > 100){MainFrame.nodes = (ArrayList<Node>) MainFrame.nodes_backup.clone(); MainFrame.process();}
      else if(width-mouseX >  50){MainFrame.delete(true); MainFrame.process();}
      else if(width-mouseX >   0){MainFrame.delete(false); MainFrame.process();}
    }else if(mouseY < 450){ 
      if     (width-mouseX > 100){}// COL_MODE = (COL_MODE+1)%4; for(NodeCurve n : MainFrame.node_curves){n.apply_color();} }
      else if(width-mouseX >  50){  }
      else if(width-mouseX >   0){ show_all^=true; }
    }
    else if (mouseY > height-450 && mouseY < height-150){ // miniscreen area
      int x = (mouseY - height + 450)/100 + view_place; // id of the miniscreen clicked on
      if(x >= screen_sources.size()){return;} // sanity check, avoid loading below the last
      loadSetting(screen_sources.get(x) + ".setting");
    }
    else if (mouseY > height-150 && mouseY < height-100){ // bottom, Load/Save row
      if(mouseX < width-75){openStuff();} // Load
      else if(mouseX > width-75){saveStuff();} // Save
    }
    println("controls clicked completed");
  }
  public void mouseWheel(MouseEvent event){
    if(!isOpen || mouseY>height-100 || mouseX < width-150){return;} // shouldn't be on the ControlWindow 
    if(mouseY > 250 && mouseY < 300){ // first scroll row
        if     (width-mouseX > 100){COL_MODE = (4+COL_MODE + event.getCount())%4; for(NodeCurve n : MainFrame.node_curves){n.apply_color();} }
        else if(width-mouseX >  50){MainFrame.FILTER.mode = (5+MainFrame.FILTER.mode + event.getCount())%5;}
        else if(width-mouseX >   0){ALT_TIME_WINDOW = max(0, min(60, ALT_TIME_WINDOW + 1*event.getCount()));}
    }else if(mouseY > 300 && mouseY < 350){ // second scroll row
        if     (width-mouseX > 100){ BIG_K = min(100.0f, max(0.001f, BIG_K * exp(0.2f*event.getCount())));}
        else if(width-mouseX >  50){ // BRIEF MIN
          float NEW_BRIEF_MIN = max(0.4f, min(2000, BRIEF_MIN * pow(1.1f,event.getCount())));
          for(NodeCurve n : MainFrame.node_curves){ if(n.dist<BRIEF_MIN != n.dist<NEW_BRIEF_MIN){n.apply_color();} }
          BRIEF_MIN = NEW_BRIEF_MIN;
        }
        else if(width-mouseX >   0){ SIZE = min(100.0f, max(1.0f, SIZE * exp(0.2f*event.getCount())));}
    }else if(mouseY > height-450 && mouseY < height-150){ // we are in the miniscreens bar
      view_place = max(min(view_place + event.getCount(), screens.size()-3), 0); // scroll through, limited to a range
    }
  }
}
class Filter{ // capsule for all the filtering behavior
  ArrayList<Lens> Lenses;
  int selected = 0, selected_time = 0;
  int mode = 0; // numeric 0-4, decides what filtering to do to saccades between potentially multiple lenses (and also the foreground fixations)
  Frame f;
  ArrayList<Integer> inst = new ArrayList<Integer>();
  Filter(Frame f){ Lenses = new ArrayList<Lens>(); this.f=f;}
  public void apply_copy(Frame f){
    Filter fi = new Filter(f);
    for(Lens l: Lenses){
      Lens li = new Lens(f);
      li.mX = l.mX; li.mY=l.mY; li.LS=l.LS; li.WINDOW = l.WINDOW; li.FACTOR = l.FACTOR;
      fi.Lenses.add(li);
    }
    f.FILTER = fi; f.FILTER.update_all();
  }
  public void draw(){
    for(int i=0;i<Lenses.size();i++){Lenses.get(i).draw(i==selected);}
    if(Lenses.size()>0){ // draw the line marking the selected lens
      f.base.stroke(grey(50)); f.base.strokeWeight(2);
      float h = height - 100 + (60.0f*selected + 30)/Lenses.size();
      f.base.line(100, h, width-100, h);
    }
  }
  
  public void update_all(){
    for(Lens l : Lenses){l.update(l.mX, l.mY);}
    for(NodeCurve n : f.node_curves){n.altt=true;}
  }
  public void move(int mX, int mY){
    if(Lenses.size()>0){
      int current = 0;
      if(Lenses.get(selected).inside(mX, mY)){
        current=selected;
      }else{
        float mindist = sq(mX-Lenses.get(0).mX) + sq(mY-Lenses.get(0).mY);
        float newdist = 0;
        for(int i=1;i<Lenses.size();i++){
          newdist = sq(mX-Lenses.get(i).mX) + sq(mY-Lenses.get(i).mY);
          if(newdist < mindist){mindist=newdist;current=i;}
        }
      }
      if(current!=selected){selected=current;selected_time=millis();}
      else if(millis() - selected_time > 150){Lenses.get(selected).update(mX,mY);}
    }
  }
  public void adjust(MouseEvent event){
    if(Lenses.size()>0){
      Lens LENS = Lenses.get(selected);
      LENS.adjust(event);//let the lens apply the rules for reshaping itself
      update_all();
    }
  }
  
  public boolean can_draw_curve(NodeCurve G){
    if( !f.TIME.within(G.t) ){return false;} // no drawing, since out of time frame
    if( !f.TIME.within(G.t + G.dt) ){return false;}
    //if( G.dist < BRIEF_MIN && !SHOW_SHORT ){return false;} // short is hidden
    if( G.dist < BRIEF_MIN){ return SHOW_SHORT; }
    if( G.dist >= BRIEF_MIN ){if( (!SHOW_GLANCE && G.is_glance_edge()) || (!SHOW_BASIC && !G.is_glance_edge()) ){return false;}} // type is hidden
    if(Lenses.size()==0){return true;} // degenerate case
    selected = min(selected, Lenses.size()-1); // sanity check
    if(mode==0){ // made up only-use-selected-guy situation for setting up new architecture with
      Lens LENS = Lenses.get(selected);
      if(G.dist<BRIEF_MIN){
        return LENS.safe_t(G.x1t[selected],G.y1t[selected], G.xst[selected][G.xst[selected].length-1], G.yst[selected][G.yst[selected].length-1]);
      }else{ // use the LENS accept function to check if I cross the line
          return LENS.accept_t(G.x1t[selected], G.y1t[selected], G.xst[selected], G.yst[selected]);
      }
    }else if(mode==1){ // replacing effects of F
      Lens LENS = Lenses.get(selected);
      return LENS.inside(G.x1,G.y1) != LENS.inside( G.xs[G.xs.length-1],G.ys[G.xs.length-1] );
    }else if(mode==2){
      for(Lens LENS : Lenses){
        if( LENS.inside(G.x1,G.y1) != LENS.inside( G.xs[G.xs.length-1],G.ys[G.xs.length-1] ) ){return true;};
      }
      return false;
    }else if(mode==3){
      for(int i=0;i<Lenses.size();i++){
        if( !Lenses.get(i).accept_t(G.x1t[i], G.y1t[i], G.xst[i], G.yst[i]) ){return false;}
      }
      return true;
    }else if(mode==4){
      boolean has_left=false, has_right=false;
      for(Lens LENS : Lenses){
        if(LENS.inside(G.x1,G.y1) && (!LENS.inside( G.xs[G.xs.length-1],G.ys[G.xs.length-1] ))){has_left=true;}
        if((!LENS.inside(G.x1,G.y1)) && LENS.inside( G.xs[G.xs.length-1],G.ys[G.xs.length-1] )){has_right=true;}
      }
      return has_left && has_right;
    }
    return false;
  }
  
  public void foreground(boolean isgen){
    if(Lenses.size()==0){return;} // degenerate case, do nothing
    inst = new ArrayList<Integer>();
    for(int i=0;i<f.nodes.size();i++){
      ArrayList<Integer> results = new ArrayList<Integer>();
      for(int k=0;k<Lenses.size();k++){
        if(isgen){results.add(f.nodes.get(i).general_value(Lenses.get(k)));}
        else{results.add(f.nodes.get(i).alternate_value(Lenses.get(k)));}
      }
      if(mode==0 || mode==1){ // basic mode, only uses the selected lens
        inst.add(results.get(selected));
      }else if(mode==2){ // treats all the lenses as one big shared lens
        int calculated = 0;
        for(int r : results){
          if(calculated==0 || r==1){calculated=r;} // white dominates, grey loses to anything
          else if(calculated==2 || r==2 || calculated+r==7){calculated=2;} // glance between lenses
        }
        inst.add(calculated);
      }else if(mode==3 || mode==4){
        boolean has_left=false; boolean has_right=false; boolean has_in=false;
        for(int r : results){

        if(r==1){has_in=true;}
          else if(r==2){has_left=true;has_right=true;}
          else if(r==3){has_left=true;}
          else if(r==4){has_right=true;}
        }
        if(!has_in){inst.add(0);}
        else if(!has_left && !has_right){inst.add(0);}
        else if(has_left && has_right){inst.add(2);}
        else if(has_left){inst.add(3);}
        else if(has_right){inst.add(4);}
        else{inst.add(1);}
      }else{
        inst.add(0);
      }
    }
    // hist stuff can go here
    float tval = 0;
    if(LHIST || THIST){ tval = min(1, (millis()-swap_time)/1000); } else { tval = 1 - min(1, (millis()-swap_time)/1000); }
    if(tval>0){
      if(THIST||PHIST){f.timeHist(inst, tval, isgen);}
      else{f.lensHist(inst, tval, isgen);}
    }
    // draw normally
    for(int i=0;i<f.nodes.size();i++){
      if( tval<=0  || inst.get(i)==1){
        if(isgen){ f.nodes.get(i).draw_self(inst.get(i), 0); }
        else{ f.nodes.get(i).draw_self(inst.get(i), 1); }
      }
      if(TRAVEL_LINES && inst.get(i)==3 && inst.get(i+1)==1){ // the Travel lines
        int k = i+1; strokeWeight(2);
        while(k<f.nodes.size() && (inst.get(k)==1 || inst.get(k)==2) ){k++;}
        if(k<f.nodes.size() && inst.get(k)==4){
          Node ni = f.nodes.get(i); Node nk = f.nodes.get(k);
          f.base.stroke(o1(60)); f.base.line(ni.x, ni.y, (ni.x+nk.x)/2, (ni.y+nk.y)/2);
          f.base.stroke(o3(60)); f.base.line(nk.x, nk.y, (ni.x+nk.x)/2, (ni.y+nk.y)/2);
        }
      }
    }
  }
  public void filtered_timeline(){
    strokeWeight(1);
    if(Lenses.size()>1){ // draw the selection line
      strokeWeight(3); stroke(grey(30));
      float y = height-100 + (60.0f*selected + 30)/Lenses.size();
      line( TBUFFER, y, TBUFFER+TWIDTH, y);
    }
    if(Lenses.size()==0 || (!ALTERNATE&&!GENERAL) ){ // degenerate case, just make grey marks
      for(int i=0;i<f.nodes.size();i++){
        f.nodes.get(i).time_mark(0, 0, 1);
      }
    }else if(ALTERNATE){ // use the alternate valuation
      for(int k=0;k<Lenses.size();k++){
        for(int i=0;i<f.nodes.size();i++){
          f.nodes.get(i).time_mark(f.nodes.get(i).alternate_value(Lenses.get(k)), k, Lenses.size());
        }
      }
    }else{ // use the general valuation
      for(int k=0;k<Lenses.size();k++){
        for(int i=0;i<f.nodes.size();i++){
          f.nodes.get(i).time_mark(f.nodes.get(i).general_value(Lenses.get(k)), k, Lenses.size());
        }
      }
    }
    // saccades
    for(NodeCurve n : f.node_curves){if(n.active){n.time_mark();}} // time marks hide behind the timeline
  }
  public void connectors(){
    if(inst.size()<f.nodes.size()){/*println(inst.size(), f.nodes.size());*/ return;}
    strokeWeight(2);
    for(int i=0;i<f.nodes.size();i++){
      int val = inst.get(i);
      Node n = f.nodes.get(i);
      float s = SIZE;
      if(USE_SIZE){s = s*sqrt(n.dt);}
      if(val > 0 && sq(mouseX-n.x) + sq(mouseY-n.y) < sq(s/2)){
          if(val==1){fill(white(100));stroke(white(100));}//white
          else if(val==2){stroke(o2(80));}//orange
          else if(val==3){stroke(o1(80));}//red
          else if(val==4){stroke(o3(80));}//green
          line(n.x,n.y, TBUFFER+(n.t-f.TIME.Tmin)*TWIDTH/(f.TIME.Tmax - f.TIME.Tmin), height-100);
      }
    }
  }
}
class Frame{
  // will encapsulate the entire state, for the purpose of small multiples
  
  // background image information
  PImage bgi;
  float fx=0, fy=0; // resizing ratios. Needed for resizing the node lists using the bgi size
  // node and bundle information
  ArrayList<Node> nodes = new ArrayList<Node>();
  ArrayList<Node> nodes_backup = new ArrayList<Node>();
  ArrayList<NodeCurve> node_curves = new ArrayList<NodeCurve>();
  // video information
  Movie clip;
  PImage clip_frame;
  float movie_update_time=0, movie_start=0;
  // structures and other capsules
  Filter FILTER = new Filter(this); Time TIME = new Time(); Notes notes;
  LowerLayer lower; PGraphics base;
  int id=0;
  Frame(int id){
    this.id = id;
    lower = new LowerLayer(this); notes  = new Notes(this);
    base = createGraphics(1700, 900);
    //GPU setup safety catch, needed for handling the demon bug on my device, probably isn't needed elsewhere
    (new GPUSetup()).start(); // thread("GPUsetup"); // tries its best to setup the openCL behaviour, but can freeze due to the demon bug, so threaded
    base.colorMode(HSB, 100);
  }
  
  public void inner_draw(){
    // do bundling thread if needed and not in progress, then do update thread (if anything has changed) and rebuild the lower layer
    if(BUNDLE && !is_bundling){(new autobundle()).start();} // bundling might take multiple frames per round, but we still want a fast foreground, so it gets a thread
    else if(!BUNDLE && is_bundling){is_bundling=false;} // current thread will either terminate or freeze, but we still want to be able to make new ones
    // update the edge acceptance information (threaded for a bit more speed), then rebuild and print the lower component
    if( z!=1 && (pmouseX!=mouseX || pmouseY!=mouseY) ){lower.has_changed=true;} // if we moved the mouse while zoomed, need to update
    if(!is_activating && lower.has_changed){(new UpdateActive()).start(); lower.build();} // first condition will almost never fail, don't worry about it
    base.background(lower.base.copy());
    // draw the foreground features (animation, connecting symbols, etc., quick to draw and needing regular updating)
    if(z!=1){translate(-mouseX*(z-1), -min(mouseY,height-100)*(z-1));scale(z);} // zoom for the foreground components is handled here
    if(ALTERNATE){FILTER.foreground(false);}
    if(GENERAL){FILTER.foreground(true);}
    if(ANIMATE){for(NodeCurve n : node_curves){n.firefly();}}
    FILTER.draw(); // draw the Filter lens circles
    if(z!=1){scale(1/z);translate(mouseX*(z-1), min(mouseY,height-100)*(z-1));} // rest of the components are the interface, don't need to have zooming
    notes.draw(); // draw notes
  }
  public void draw(){
    base.beginDraw();
    if(bgi==null){ // user needs to load a background image before we can even begin.
      base.fill(white(100)); base.textFont(f); base.background(black(100));
      base.text( startmess, base.width/2 - 200, base.height/2 - 50 );
    }else if(KunTingMode){
      if(clip!=null && clip.available()){
      if(movie_update_time != 0){clip.jump(movie_update_time);movie_update_time=0;}
      if( clip.time() < TIME.start() || clip.time() > TIME.end() ){clip.jump(TIME.start());}
      clip.read(); clip_frame = clip.get(); clip_frame.resize(base.width, base.height);
      if(clip_frame!=null){bgi = clip_frame;}
      }else{ bgi = blank; bgi.resize(base.width, base.height);
      }
      inner_draw();
    }else if(CLIP_MODE && clip!=null && clip.available()){//draw the movie screen
      if(movie_update_time != 0){clip.jump(movie_update_time);movie_update_time=0;}
      if( clip.time() < TIME.start() || clip.time() > TIME.end() ){clip.jump(TIME.start());}
      clip.read(); clip_frame = clip.get(); clip_frame.resize(lower.base.width, lower.base.height);
      float f = min(100, (millis()-movie_start)/50);
      if(f<100){base.image(lower.base, 0, 0);}
      base.tint(100, f); base.image(clip_frame, 0, 0); base.noTint();
    }else{ // main mode
      inner_draw();
    }
    if(show_all && selected_frame==id){
      base.noFill();base.stroke(white(100));base.strokeWeight(2);
      base.rect(0,0,base.width,base.height);
    }
    base.endDraw();
  }
    
  // updates the node information before the lower layer uses it, runs in its own thread
  public class UpdateActive extends Thread{ public void run(){
    is_activating=true; float start_t = millis();
    for(NodeCurve n: node_curves){
      if(n.altc){n.update_content(); n.update_t(); lower.has_changed = true;}
      if(n.altt){n.update_t();}
      boolean g = FILTER.can_draw_curve(n);
      if(n.active != g){n.active=g; lower.has_changed=true;}
    }
    is_activating=false; println("updated in ", millis()-start_t);
  }}
  
  // handles the bundling functionality
  // objects needed for GPU based bundling processing (initialised by the GPUsetup function)
  boolean GPU_setup = false; // boolean describing if the GPU context information has been set up yet
  cl_context context; cl_command_queue queue;
  cl_program program; cl_kernel inner, outer, inner_directed, current;
  
  // calls the bundle manager, needed for threading
  public class autobundle extends Thread{ public void run(){
    float start_t = millis(); println("Begin buldling a Frame");
    is_bundling=true;
    bundle_manager(2);
    is_bundling=false; lower.has_changed = true; println("bundled in ", millis()-start_t);
  }}
  public class GPUSetup extends Thread{ public void run(){
    println("start GPUsetup");
    final int platformIndex = 0;
    final long deviceType = CL_DEVICE_TYPE_ALL;
    final int deviceIndex = 0;
  
    CL.setExceptionsEnabled(true);
    int numPlatformsArray[] = new int[1];
    clGetPlatformIDs(0, null, numPlatformsArray);
    int numPlatforms = numPlatformsArray[0];
  
    // Obtain a platform ID
    cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
    clGetPlatformIDs(platforms.length, platforms, null);
    cl_platform_id platform = platforms[platformIndex];
  
    // Initialize the context properties
    cl_context_properties contextProperties = new cl_context_properties();
    contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);
  
    // Obtain the number of devices for the platform
    int numDevicesArray[] = new int[1];
    clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
    int numDevices = numDevicesArray[0];
  
    // Obtain a device ID 
    cl_device_id devices[] = new cl_device_id[numDevices];
    clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
    cl_device_id device = devices[deviceIndex];
  
    // Create a context for the selected device
    context = clCreateContext(contextProperties, 1, new cl_device_id[] {device}, null, null, null);
    queue = clCreateCommandQueue(context, device, 0, null);
    program = clCreateProgramWithSource(context, 1, new String[] {src_cl}, null, null);
    clBuildProgram(program, 0, null, null, null, null);
    
    inner = clCreateKernel(program, "inner", null); outer = clCreateKernel(program, "outer", null);
    inner_directed = clCreateKernel(program, "inner_directed", null);
    GPU_setup = true;
  }}
  
  
  public void dynamic_bundling(float[] maxstep, int num_nodes, boolean[] included, boolean directed){
    println("mark1");
    float[] xb = new float[num_nodes], yb = new float[num_nodes], lb = new float[num_nodes], fb = new float[2*16*num_nodes];
    Pointer xPtr = Pointer.to(xb), yPtr = Pointer.to(yb), lPtr = Pointer.to(lb), fPtr = Pointer.to(fb);
    // extract values from the curve arraylist, place in the buffers
    int up_to = 0;
    for(int i=0;i<node_curves.size();i++){
      NodeCurve c = node_curves.get(i);
      if( included[i] ){
        xb[up_to] = c.x1; yb[up_to] = c.y1; lb[up_to] = 1.0f; up_to += 1;
        for(int j=0;j<c.xs.length-1;j++){ xb[up_to] = c.xs[j]; yb[up_to] = c.ys[j]; lb[up_to] = 0.0f; up_to+=1; }
        xb[up_to] = c.xs[c.xs.length-1]; yb[up_to] = c.ys[c.xs.length-1]; lb[up_to] = 1.0f; up_to += 1;
      }
    }
    cl_mem[] memObjects = new cl_mem[4];
    memObjects[0] = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR, Sizeof.cl_float * xb.length, xPtr, null);
    memObjects[1] = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR, Sizeof.cl_float * yb.length, yPtr, null);
    memObjects[2] = clCreateBuffer(context, CL_MEM_READ_ONLY  | CL_MEM_COPY_HOST_PTR, Sizeof.cl_float * lb.length, lPtr, null);
    memObjects[3] = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR, Sizeof.cl_float * fb.length, fPtr, null);
    
    if(directed){current=inner_directed;}else{current=inner;}
    // Allocate the memory objects for the input- and output data
    for(int i=0;i<4;i++){
      clSetKernelArg(outer,   i, Sizeof.cl_mem, Pointer.to(memObjects[i]));
      clSetKernelArg(current, i, Sizeof.cl_mem, Pointer.to(memObjects[i]));
    }
    clSetKernelArg(current, 4, Sizeof.cl_int, Pointer.to(new int[] {num_nodes}));
    clSetKernelArg(current, 5, Sizeof.cl_float, Pointer.to(new float[] {BIG_K}));
    clSetKernelArg(outer, 4, Sizeof.cl_int, Pointer.to(new int[] {num_nodes}));
    println("mark4");
    //if(1+1==2){return;}
    //current.setArgs(xb, yb, lb, fb, num_nodes, BIG_K);
    for(int iteration=0;iteration<maxstep.length;iteration++){ // iteratively bundle with values maxstep from the regime array
      clEnqueueNDRangeKernel(queue, current, 1, null, new long[] {num_nodes, 16}, new long[] {1, 1}, 0, null, null);
      clEnqueueReadBuffer(queue, memObjects[3], CL_TRUE, 0, Sizeof.cl_float * fb.length, fPtr, 0, null, null);
      //addEvt = current.enqueueNDRange(queue, new int[] { num_nodes, 16 });
      clSetKernelArg(outer, 5, Sizeof.cl_float, Pointer.to(new float[] {maxstep[iteration]}));
      clEnqueueNDRangeKernel(queue, outer, 1, null, new long[] {num_nodes}, new long[] {1}, 0, null, null);
      clEnqueueReadBuffer(queue, memObjects[0], CL_TRUE, 0, Sizeof.cl_float * xb.length, xPtr, 0, null, null);
      clEnqueueReadBuffer(queue, memObjects[1], CL_TRUE, 0, Sizeof.cl_float * yb.length, yPtr, 0, null, null);
      //fPtr = fb.read(queue, addEvt); // blocks until inner finished
      //addEvt = outer.enqueueNDRange(queue, new int[] { num_nodes });
      //xPtr = xb.read(queue, addEvt); yPtr = yb.read(queue, addEvt); // blocks until outer finished
    }
    //then return updated values back into the curve arraylist
    up_to = 0;
    for(int i=0;i<node_curves.size();i++){
      NodeCurve c = node_curves.get(i);
      if( included[i] ){
        up_to+=1;//skip over the first guy, he is locked anyway
        for(int j=0;j<c.xs.length-1;j++){ c.xs[j] = xb[up_to]; c.ys[j] = yb[up_to]; up_to+=1; }
        up_to+=1;//skip over the last guy, he is locked anyway
        c.altc= true;
      }
    }
    println("mark5");
    clReleaseMemObject(memObjects[0]);
    clReleaseMemObject(memObjects[1]);
    clReleaseMemObject(memObjects[2]);
    clReleaseMemObject(memObjects[3]);
  }
  
  // some options for maxstep regimes. Each float array describes the stepsizes of the bundling rounds
  float[] s1 = {1,0.9f,0.8f,0.7f,0.6f,0.5f,0.4f,0.3f,0.2f,0.1f,0.09f,0.08f,0.07f,0.06f,0.05f,0.04f,0.03f,0.02f,0.01f,0.009f,0.008f,0.007f,0.006f,0.005f,0.004f,0.003f,0.002f,0.001f};
  float[] s2 = {4,3.5f,3.0f, 2.6f,2.3f,2.0f, 1.7f,1.4f,1.2f, 1.0f,0.6f,0.4f};
  float[] s3 = {100,80,64,51.2f,41,32.8f,26.2f,21,16.8f,13.4f,10.7f,8.56f,6.85f,5.48f,4.38f,3.5f,2.45f,1.71f,1.2f,0.84f,0.588f,0.411f,0.2f};
  public void bundle_manager(int num){
    if(!GPU_setup){return;} // no way to do the bundling if the GPU setup failed, so just return
    float[] s;
    switch(num){
      case 1: s = s1; break;
      case 2: s = s2; break;
      case 3: s = s3; break;
      default: return;
    }
    // make the list of included flags, and calculate the size (to avoid doing multiple times)
    boolean[] included = new boolean[node_curves.size()];
    int num_nodes = 0;
    switch(COL_MODE){ // switch for what kind of bundling to apply
      case 0: // everything together
        for(int i=0;i<node_curves.size();i++){
          NodeCurve c = node_curves.get(i);
          included[i] = c.active && c.xs.length>2 && c.dist>BRIEF_MIN;
          if( included[i] ){num_nodes+=(1+c.xs.length);}
        }
        if(num_nodes > 0){dynamic_bundling(s, num_nodes, included, false);}
        break;
      case 1: // type based bundling
        for(int i=0;i<node_curves.size();i++){
          NodeCurve c = node_curves.get(i);
          included[i] = c.active && c.xs.length>2 && c.dist>BRIEF_MIN && c.is_glance_edge();
          if( included[i] ){num_nodes+=(1+c.xs.length);}
        }
        if(num_nodes > 0){dynamic_bundling(s, num_nodes, included, false);} // do the glances first
        included = new boolean[node_curves.size()]; num_nodes = 0; // reset for the second partition
        for(int i=0;i<node_curves.size();i++){
          NodeCurve c = node_curves.get(i);
          included[i] = c.active && c.xs.length>2 && c.dist>BRIEF_MIN && !c.is_glance_edge();
          if( included[i] ){num_nodes+=(1+c.xs.length);}
        }
        if(num_nodes > 0){dynamic_bundling(s, num_nodes, included, false);} // then the non-glances
        break;
      case 2: // angle base bundling
        for(int i=0;i<node_curves.size();i++){
          NodeCurve c = node_curves.get(i);
          included[i] = c.active && c.xs.length>2 && c.dist>BRIEF_MIN;
          if( included[i] ){num_nodes+=(1+c.xs.length);}
        }
        if(num_nodes > 0){dynamic_bundling(s, num_nodes, included, true);}
        break;
    }
  }
  // builds the node_curves from the nodes data, used on new CSV files or to clear the bundling
  public void process(){
    node_curves = new ArrayList<NodeCurve>(nodes.size());
    for(int i=0;i<nodes.size()-1;i++){
      NodeCurve current = new NodeCurve( nodes.get(i), nodes.get(i+1), this );
      if(node_curves.size()>0){;
        current.prev(node_curves.get(node_curves.size()-1));
        node_curves.get(node_curves.size()-1).next(current);
      }
      node_curves.add(current);
    }
    for(NodeCurve n : node_curves){n.make_content();} // Glance status is known at this point in time
  }
  public void delete(boolean interior){
    ArrayList<Node> newnodes = new ArrayList<Node>();
    for(Node n : nodes){
      if(TIME.within(n.t) && (FILTER.Lenses.size()==0 || FILTER.Lenses.get(FILTER.selected).inside(n.x, n.y)==interior) ){
        n.remove = true;
      }else{
        newnodes.add(n);
      }
    }
    nodes = newnodes;
    process();
  }
  
  // hist stuff
  
  public void timeHist(ArrayList<Integer> inst, float tval, boolean isgen){
    float level=0; int old_bin = 0, BINS = 140;
        for(int i=0;i<nodes.size();i++){
          if(inst.get(i)>1){
            Node n = nodes.get(i);
            int bin = floor(BINS*(n.t - TIME.Tmin)/(TIME.Tmax - TIME.Tmin));
            if(bin!=old_bin){old_bin=bin;level=0;}else{level += SIZE;}
            if(isgen){n.t_animate_mark(inst.get(i), 0, level, tval, BINS);}
            else{     n.t_animate_mark(inst.get(i), 1, level, tval, BINS);}
          }
        }
  }
    
  public void lensHist(ArrayList<Integer> inst, float tval, boolean isgen){
    if(FILTER.Lenses.size()==0){return;}
    Lens l = FILTER.Lenses.get(FILTER.selected);
    int BINS = 120; float[] vals = new float[BINS];
    for(int i=0;i<BINS;i++){
      float angle = TWO_PI*((i+0.5f)/BINS);
      // it would be cool to have an explicit formula here, and I spent a while trying to find one, but alas I could not.
      vals[i] = l.WINDOW/2; float step = 2; // instead lets do a search in each direction for the lens border
      for(int k=1;k<30;k++){
        if( l.inside(l.mX + cos(angle)*vals[i], l.mY + sin(angle)*vals[i]) ){ vals[i]*=step; }else{ vals[i]/=step; } step= 1 + 0.7f*(step-1);
      }
      vals[i] += SIZE;
    }
    ArrayList<Node>[] buckets = new ArrayList[BINS];
    for(int i=0;i<BINS;i++){buckets[i] = new ArrayList<Node>();}
    for(int i=0;i<nodes.size();i++){ // distribute nodes into the bins
      if(inst.get(i)>1){
        Node n = nodes.get(i); n.inst = inst.get(i);
        int bin = floor(BINS*(PI+atan2(l.mY-n.y, l.mX-n.x))/TWO_PI) % BINS; buckets[bin].add(n);
      }
    }
    for(int bin=0;bin<BINS;bin++){ // sort and draw calculated in the sorted order
      NodeSort(buckets[bin], l.mX, l.mY);
      for(Node n : buckets[bin]){
        if(isgen){n.t_animate_mark_2(n.inst, 0, tval, l.mX+ cos(TWO_PI*(bin+0.5f)/BINS)*vals[bin], l.mY+ sin(TWO_PI*(bin+0.5f)/BINS)*vals[bin]);}
        else{     n.t_animate_mark_2(n.inst, 1, tval, l.mX+ cos(TWO_PI*(bin+0.5f)/BINS)*vals[bin], l.mY+ sin(TWO_PI*(bin+0.5f)/BINS)*vals[bin]);}
        vals[bin] += SIZE;
      }
    }
  }
}

public ArrayList<Node> NodeSort(ArrayList<Node> n, float x, float y){ // sub method for distance based sorting
  // n < 100 or so, so I'll just use a naive O(n^2) sort
  for(int i=0; i<n.size()-1; i++){
    float d1 = sq(n.get(i).x - x) + sq(n.get(i).y - y);
    for(int j=i+1; j<n.size(); j++){
      float d2 = sq(n.get(j).x - x) + sq(n.get(j).y - y);
      if(d1 > d2){ // do the swap
        d1 = d2;
        Node a = n.get(i); Node b = n.get(j);
        n.set(i, b); n.set(j, a);
      }
    }
  }
  return n;
}

public int num_divs(float dist){return floor(max((dist/30.0f)-1, 2)); } // how many intermediate points to make, min of 2 to render properly
class Lens{
  // gonna need to make lenses carry their own paraproperties before they can have multiple. Which means I need a good way of resizing?
  float WINDOW = 400; // the general size of the window
  float FACTOR = 0; // the ovalness of the window
  float LS=2; // the p-shape of the lens

  float mX=300; float mY=300;
  float mXt=2*300/WINDOW; float mYt=2*300/WINDOW;
  Frame f;
  Lens(Frame f){
    this.f=f;
    if(f.FILTER.Lenses.size()>0){
      f.FILTER.selected = min(f.FILTER.selected, f.FILTER.Lenses.size()-1); // sanity check
      Lens i = f.FILTER.Lenses.get(f.FILTER.selected);
      WINDOW = i.WINDOW; FACTOR=i.FACTOR; LS=i.LS;
    }
    mX = min(max(mouseX,0), width); mY = min(max(mouseY,0), height-100);
    update(mX,mY);
  }
  public void update(float x_, float y_){ mX=x_; mY=y_; mXt = (2*x_/exp(-FACTOR))/WINDOW; mYt = (2*y_/exp(FACTOR))/WINDOW;}
  
  public void draw(boolean selected){
    f.base.noFill();
    f.base.stroke(white(100));
    f.base.strokeWeight(1);
    if(selected){f.base.strokeWeight(3);}
    float fX = exp(-FACTOR)*WINDOW/2, fY = exp(FACTOR)*WINDOW/2;
    if(LS==32.0f){ f.base.rect(mX-fX, mY-fY, 2*fX, 2*fY);} // much easier to draw a rectangle
    else{
      f.base.beginShape(); // inb4 this is bad coding practice
      for(float i=-0.5f;i<0.5f;i=i+0.02f){f.base.vertex(mX+fX*(i/abs(i))*pow(  abs(i), 1/LS),mY+fY*           pow(1-abs(i), 1/LS));}
      for(float i=-0.5f;i<0.5f;i=i+0.02f){f.base.vertex(mX+fX*           pow(1-abs(i), 1/LS),mY-fY*(i/abs(i))*pow(  abs(i), 1/LS));}
      for(float i=-0.5f;i<0.5f;i=i+0.02f){f.base.vertex(mX-fX*(i/abs(i))*pow(  abs(i), 1/LS),mY-fY*           pow(1-abs(i), 1/LS));}
      for(float i=-0.5f;i<0.5f;i=i+0.02f){f.base.vertex(mX-fX*           pow(1-abs(i), 1/LS),mY+fY*(i/abs(i))*pow(  abs(i), 1/LS));}
      f.base.endShape(CLOSE);
    }
    float t_inside=0, t_total = 0;
    for(Node n: f.nodes){ if(f.TIME.within(n.t)){t_total+=n.dt; if(inside(n.x,n.y)){t_inside+=n.dt;}} }
    f.base.fill(white(100)); f.base.textFont(fb);
    //f.base.text( String.format("%.02f", (100*t_inside/t_total))+"%", mX-15, mY + WINDOW*exp(FACTOR)/2 - 10);
  }
  // whether an untransformed point is inside the lens shape
  public boolean inside(float x, float y){
    return inside_t( 2*x/exp(-FACTOR)/WINDOW, 2*y/exp(FACTOR)/WINDOW );
  }
  public boolean inside_t(float x, float y){ // whether a point is inside the lens shape, that has been transformed
    float dx = abs(x-mXt); float dy = abs(y-mYt);
    if(LS==32.0f){ return dx<1 && dy<1; } // the lens is a true square, so use infinity norm
    if( dx>1 || dy>1 ){ return false; } // not even in the square, so no chance
    return pow(dx, LS)+pow(dy, LS) < 1; // use the LS P-norm
  }
  // whether a line crosses the lens boundary
  public boolean safe_t( float x1, float y1, float x2, float y2){
    boolean in1 = inside_t(x1,y1); boolean in2 = inside_t(x2,y2);
    if(in1 & in2){return true;}  // since convex
    if(in1 != in2){return false;} // clearly crosses the lens, since swaps sides
    if( min(x1,x2)>mXt+1 | max(x1,x2)<mXt-1 | min(y1,y2)>mYt+1 | max(y1,y2)<mYt-1 ){return true;} // entirely on one side
    int N = 30;
    for(int i=1;i<N-1;i++){
      if(inside_t( (x1*i + x2*(N-i))/N, (y1*i + y2*(N-i))/N )){return false;} // intercept found by search
    }
    return true; // default to being trusting ( we checked quite a few points, so it is probably fine )
  }
  
  //whether a set of curve features cross the lens or not
  public boolean accept_t(float x1, float y1, float[] xs, float[] ys){
    boolean sign = inside_t(x1,y1); // determines whether we must stay inside, or outside the lens
    // iterate over the nodes along the curve, checking to see if we swap sides of the lens edge
    for(int i=0;i<xs.length;i++){
      if(inside_t(xs[i], ys[i]) != sign){return false;} // we swapped sides here
    }
    if(sign){return true;} // since the lens is entirely convex, and the line segments straight, this is enough
    //check each line segment
    if( !safe_t(x1,y1,xs[0], ys[0]) ){return false;} //first line segment crosses it
    // otherwise, chance a pair of edge-nodes straddle part of the lens from the exterior
    for(int i=0;i<xs.length-1;i++){
      if( !safe_t(xs[i], ys[i], xs[i+1], ys[i+1]) ){ return false; }
    }
    return true; // passes all the tests
  }
  
  public void adjust(MouseEvent event) {
    if( (mousePressed && rmp) ||  (keyPressed && key=='m') ){ // right mouse key adjustment
      LS = LS*exp(-log(2)*event.getCount()/3); // modify shape
      LS = min(32, max(1, LS));
    }else if(  (mousePressed && lmp) ||  (keyPressed && key=='n') ){ // left mouse key adjustment
      WINDOW = WINDOW - (5 + WINDOW/15)*event.getCount(); // modify size
      WINDOW = min(2000*exp(abs(FACTOR)), max(15, WINDOW));
    }else{ // no mouse key adjustment
      FACTOR = FACTOR - 0.05f*event.getCount(); // modify squish
      FACTOR = min(5, max(-5, FACTOR));
    }
    update(mX, mY);
  }
}
class LowerLayer{
  PGraphics base;
  PImage bgr = null;
  boolean has_changed = true;
  Frame f;
  LowerLayer(Frame f){base=createGraphics(1700, 900, P2D); this.f=f;}
  
  // builds most of image, all except interface and foreground features that aren't very time-consuming to draw
  public void build(){
    float start_t = millis();
    has_changed = false; // do at the start, in case it changes again before we are done
    base.noSmooth();
    base.beginDraw();
    if(z!=1){ // resizes the background image if we have zoomed in
      bgr = f.bgi.copy();
      bgr.resize(floor(z*f.bgi.width),0);
      bgr = bgr.get(floor(min(mouseX,base.width)*(z-1)), floor(min(mouseY,base.height)*(z-1)), base.width, base.height);
      base.background(bgr);
    }
    else{base.background(f.bgi);}
    // draw a darker filter over the background, to make the actual data more clear on top of it
    base.noStroke(); base.fill(0,0,0,200); base.rect(0,0,base.width,base.height);
    // apply zooming for background features
    if(z!=1){base.translate(-mouseX*(z-1), -min(mouseY,height-100)*(z-1));base.scale(z);}
    // shows the background fixation points
    if(SHOW_DOTS){for(Node n : f.nodes){ // does the background nodes
      if(f.TIME.within(n.t)){
        base.fill(c2(20));
        if(n.LastNode != null && n.NextNode != null){
          float dl = sq(n.x - n.LastNode.x) + sq(n.y - n.LastNode.y);
          float dn = sq(n.x - n.NextNode.x) + sq(n.y - n.NextNode.y);
          float db = sq(n.LastNode.x - n.NextNode.x) + sq(n.LastNode.y - n.NextNode.y);
          if(max(dl,dn)< sq(BRIEF_MIN) ){ base.fill(c3(20)); }
          else if( db*sq(GLANCE_STANDARD) < min(dl, dn) ){ base.fill(c1(20)); }
        }
        if(!USE_SIZE){base.ellipse(n.x,n.y,SIZE,SIZE);}else{base.ellipse(n.x,n.y,SIZE*sqrt(n.dt),SIZE*sqrt(n.dt));}
      }
    }}
    // shows the background saccade lines, depending on the filtering
    for(NodeCurve n : f.node_curves){
      if( n.active && n.dist<BRIEF_MIN && n.do_wheel_test() ){ // draws the short saccades first, so that they can form a background for context
        base.stroke(n.get_color(25)); base.strokeWeight(2);
        base.line(n.x1, n.y1, n.xs[n.xs.length-1], n.ys[n.ys.length-1]);
      }
    }
    if(!ANIMATE){
      for(NodeCurve n : f.node_curves){ // then draws the longer saccades on top of them
        if( n.active && n.dist>=BRIEF_MIN && n.do_wheel_test() ){
          for(PShape i : n.content){base.shape(i);}
        }
      }
    }
    base.endDraw();
    println("lower layer in ", millis()-start_t);
  }
}
class Node{
  float t,x,y,dt;
  Node NextNode = null;
  Node LastNode = null;
  boolean remove = false; // flag for deletion
  int inst = 0; float type = 1; // default type
  Frame f;
  Node(float t_, float x_, float y_, float dt_, float type_, Frame f){ t=t_;x=x_;y=y_;dt=dt_; type=type_; this.f=f; }
  public void next_node(Node next_node){ NextNode = next_node; }
  public void last_node(Node last_node){ LastNode = last_node; }
  
  public int general_value(Lens LENS){ // evaluates the nodes type in relation to a given lens, in the Generic mode
    if( !LENS.f.TIME.within(t) ){return 0;} // greyed out, since out of reference frame
    if(LENS.inside(x,y)){return 1;} // white, since in the box
    boolean BEF = LastNode!=null && LENS.inside(LastNode.x, LastNode.y), AFT = NextNode!=null && LENS.inside(NextNode.x, NextNode.y);
    if(BEF & AFT){return 2;}
    if(BEF){return 4;}
    if(AFT){return 3;}
    return 0; // greyed out, since no interesting behaviour with this lens
  }
  public int alternate_value(Lens LENS){ // evaluates the nodes type in relation to a given lens, in the Alternate mode
    if( !LENS.f.TIME.within(t) ){return 0;} // greyed out, since out of reference frame
    if( LENS.inside(x,y) ){return 1;} // white, since in the box
    float forward = ALT_TIME_WINDOW; float backward = ALT_TIME_WINDOW; 
    Node cur = this;
    while(cur!= null && abs(this.t - cur.t) < ALT_TIME_WINDOW && !LENS.inside(cur.x, cur.y)){ cur = cur.NextNode; }
    if(cur!= null && abs(this.t - cur.t) < ALT_TIME_WINDOW){forward = abs(cur.t-this.t);}
    cur = this;
    while(cur!= null && abs(this.t - cur.t) < ALT_TIME_WINDOW && !LENS.inside(cur.x, cur.y)){ cur = cur.LastNode; }
    if(cur!= null && abs(this.t - cur.t) < ALT_TIME_WINDOW){backward = abs(cur.t-this.t);}
    if(forward + backward < ALT_TIME_WINDOW){return 2;}
    if(forward < backward){return 3;}
    if(forward > backward){return 4;}
    return 0;
  }
  
  public void draw_self(int inst, int symbol){
    if(inst==0){return;}//greyed out
    else if(inst==1){f.base.fill(white(100));f.base.stroke(white(100));}//white
    else if(inst==2){f.base.fill(o2(80));f.base.stroke(o2(80));}//orange
    else if(inst==3){f.base.fill(o1(80));f.base.stroke(o1(80));}//red
    else if(inst==4){f.base.fill(o3(80));f.base.stroke(o3(80));}//green
    if(symbol==0 && gval!=-1 && inst!=gval){return;} // not the mouse-over foreground colour
    if(symbol==1 && aval!=-1 && inst!=aval){return;} // not the mouse-over foreground colour
    
    if(INTERLACE & ALTERNATE & symbol==1){//draw in the interlacing lines
      f.base.strokeWeight(2);
      if( (inst==3 || inst==2) && NextNode!=null ){ f.base.line(x,y,NextNode.x,NextNode.y); }
      if( (inst==4 || inst==2) && LastNode!=null ){ f.base.line(x,y,LastNode.x,LastNode.y); }
    }
    float s = SIZE;
    if(USE_SIZE){s = s*sqrt(dt);}
    if(inst==1){s = s*R;}
    if(symbol==0){ f.base.noStroke();f.base.ellipse(x,y,s,s); }
    else if(symbol==1){ // draws an X symbol
      f.base.strokeWeight(3); f.base.strokeCap(SQUARE);
      f.base.line(x-s/2, y-s/2, x+s/2, y+s/2);
      f.base.line(x-s/2, y+s/2, x+s/2, y-s/2);
    }
  }
  public void time_mark(int inst, int level, int num_levels){
    strokeWeight(1);
    if(inst==0){stroke(grey(50));fill(grey(50));}//greyed out
    else if(inst==1){fill(white(100));stroke(white(100));}//white
    else if(inst==2){fill(o2(80));stroke(o2(80));}//orange
    else if(inst==3){fill(o1(80));stroke(o1(80));}//red
    
    float vt = (t-f.TIME.Tmin)*TWIDTH/(f.TIME.Tmax - f.TIME.Tmin);
    float t_width = dt*TWIDTH/(f.TIME.Tmax - f.TIME.Tmin);
    float lower = height-100 + (60.0f*level)/num_levels;
    float upper = lower + 60.0f/num_levels;
    if(t_width < 2){ line( vt+TBUFFER, lower,  vt+TBUFFER, upper); } // draw timemark as a line
    else{ noStroke(); rect( vt+TBUFFER, lower,  t_width, 60.0f/num_levels ); } // draw timemark as a rect, since it is wide
    //if( TIME_PAIRS && level==FILTER.selected && inst>1 ){ line( vt+100, lower, this.x, this.y ); } // connects timemark and onscreen node
  }
  public void t_animate_mark(int inst, int symbol, float level, float at, int BINS){
    if(inst==0){return;}//greyed out
    else if(inst==1){f.base.fill(white(100));f.base.stroke(white(100));}//white
    else if(inst==2){f.base.fill(o2(80));f.base.stroke(o2(80));}//orange
    else if(inst==3){f.base.fill(o1(80));f.base.stroke(o1(80));}//red
    else if(inst==4){f.base.fill(o3(80));f.base.stroke(o3(80));}//green
    if(symbol==0 && gval!=-1 && inst!=gval){return;} // not the mouse-over foreground colour
    if(symbol==1 && aval!=-1 && inst!=aval){return;} // not the mouse-over foreground colour
    
    float td = (t-f.TIME.Tmin)/(f.TIME.Tmax-f.TIME.Tmin);
    int bin = floor(BINS*td); println(bin, td, f.TIME.Tmin, t, f.TIME.Tmax);
    float tx = TBUFFER + (bin+0.5f)/BINS*TWIDTH; float ty = f.base.height-10 - level;
    float tt = min(1, max(0, 5*at - 4*td ));
    float ix = tt*tx + (1-tt)*x, iy = tt*ty + (1-tt)*y;
    float s = SIZE;
    if(USE_SIZE){s = s*sqrt(dt);}
    if(inst==1){s = s*R;}
    if(symbol==0){ f.base.noStroke();f.base.ellipse(ix,iy,s,s); }
    else if(symbol==1){ // draws an X symbol
      f.base.strokeWeight(3); f.base.strokeCap(SQUARE);
      f.base.line(ix-s/2, iy-s/2, ix+s/2, iy+s/2);
      f.base.line(ix-s/2, iy+s/2, ix+s/2, iy-s/2);
    }
  }
  public void t_animate_mark_2(int inst, int symbol, float at, float tx, float ty){
    if(inst==0){return;}//greyed out
    else if(inst==1){f.base.fill(white(100));f.base.stroke(white(100));}//white
    else if(inst==2){f.base.fill(o2(80));f.base.stroke(o2(80));}//orange
    else if(inst==3){f.base.fill(o1(80));f.base.stroke(o1(80));}//red
    else if(inst==4){f.base.fill(o3(80));f.base.stroke(o3(80));}//green
    if(symbol==0 && gval!=-1 && inst!=gval){return;} // not the mouse-over foreground colour
    if(symbol==1 && aval!=-1 && inst!=aval){return;} // not the mouse-over foreground colour
    
    float td = (t-f.TIME.Tmin)/(f.TIME.Tmax-f.TIME.Tmin);
    Lens l = f.FILTER.Lenses.get(f.FILTER.selected);
    
    float tt = min(1, max(0, at ));
    float ix = tt*tx + (1-tt)*x, iy = tt*ty + (1-tt)*y;
    float s = SIZE;
    if(USE_SIZE){s = s*sqrt(dt);}
    if(inst==1){s = s*R;}
    if(symbol==0){ f.base.noStroke();f.base.ellipse(ix,iy,s,s); }
    else if(symbol==1){ // draws an X symbol
      f.base.strokeWeight(3); f.base.strokeCap(SQUARE);
      f.base.line(ix-s/2, iy-s/2, ix+s/2, iy+s/2);
      f.base.line(ix-s/2, iy+s/2, ix+s/2, iy-s/2);
    }
  }
}
class NodeCurve{
  float x1, y1, dt, t, dist;
  float type = 1;
  float[] xs, ys;
  PShape[] content = new PShape[0];;
  boolean active=true;//whether the filter currently approves of this curve.
  NodeCurve prev=null, next=null;
  boolean altt = true, altc = false; // any change that would require update_t() be called before next checking draw permission
  
  float[] x1t, y1t; // transformations of the locations to speed up filtering
  float[][] xst, yst;
  Frame f;
  
  NodeCurve(float x1_, float y1_, float t_, float dt_, float[] xs_, float[] ys_, float type_, Frame f){
    this.f=f;
    x1=x1_;y1=y1_;dt=dt_;xs=xs_;ys=ys_;t=t_; type=type_;
    dist = sqrt(sq(x1 - xs[xs.length-1]) + sq(y1 - ys[xs.length-1]));
    x1t = new float[MAX_LENSES]; y1t = new float[MAX_LENSES];
    xst = new float[MAX_LENSES][xs.length]; yst = new float[MAX_LENSES][xs.length];
    update_t();
  }
  NodeCurve(Node a, Node b, Frame f){
    this.f=f;
    this.type = a.type;
    x1 = a.x; y1=a.y; t=a.t; dt = b.t - (a.t + a.dt);
    dist = sqrt(sq(a.x - b.x) + sq(a.y-b.y));
    int n = num_divs(dist); // divider function, can tune this if I want more or less midpoints
    xs = new float[n]; ys = new float[n];
    for(int j=1;j<=n;j++){
      xs[j-1] = (b.x*j + a.x*(n-j))/n; ys[j-1] = (b.y*j + a.y*(n-j))/n;
    }
    x1t = new float[MAX_LENSES]; y1t = new float[MAX_LENSES];
    xst = new float[MAX_LENSES][xs.length]; yst = new float[MAX_LENSES][xs.length];
    update_t();
  }
  public void update_t(){ // updates the transformed location variables
    for(int k=0; k < f.FILTER.Lenses.size();k++){
      Lens LENS = f.FILTER.Lenses.get(k);
      float xv = 2/exp(-LENS.FACTOR)/LENS.WINDOW; float yv = 2/exp( LENS.FACTOR)/LENS.WINDOW;
      x1t[k] = x1*xv; y1t[k] = y1*yv;
      for(int i=0;i<xs.length;i++){
        xst[k][i] = xs[i]*xv; yst[k][i] = ys[i]*yv;
      }
    }
    altt=false;
  }
  public void prev(NodeCurve prev){this.prev = prev; altc=true;}
  public void next(NodeCurve next){this.next = next; altc=true;}
  public boolean do_wheel_test(){return wheel_test(atan2(ys[ys.length-1]-y1, xs[xs.length-1] - x1));}
  
  public void make_content(){
    content = new PShape[4];
    for(int j=0;j<4;j++){
      PShape P = f.lower.base.createShape();
      P.beginShape();P.noFill();P.stroke( get_color(25.0f/divs[j]) );
      P.strokeWeight(sws[j]);
      P.vertex(x1,y1);
      for(int i=0;i<xs.length;i++){P.vertex(xs[i],ys[i]); }
      P.endShape();
      content[j] = P;
    }
    apply_color();
  }
  public void apply_color(){
    if(content.length==4){
      for(int j=0;j<4;j++){
        content[j].setStroke( get_color(25.0f/divs[j]) );
      }
    }
  }
  public int get_color(float alpha){
    if( dist < BRIEF_MIN && COL_MODE < 2){return c3(alpha); }
    if(COL_MODE==1){if(is_glance_edge()){return c1(alpha);}else{ return c2(alpha);}}
    if(COL_MODE==2){ return color_wheel(alpha, atan2(y1 - ys[ys.length-1], x1 - xs[xs.length-1]));}
    if(COL_MODE==3){ return cy(alpha, type); }
    return c2(alpha); // just use one colour for all saccades
  }
  public void update_content(){
    altc = false;
    for(int j=0;j<4;j++){
      for(int i=0; i<xs.length-1;i++){ content[j].setVertex(i+1, xs[i], ys[i]);}
    }
    apply_color();
  }
  public boolean is_glance_node(){
    if(prev==null || next==null){return false;}
    float dl = sq(x1 - prev.x1) + sq(y1 - prev.y1);
    float dn = sq(x1 - next.x1) + sq(y1 - next.y1);
    float db = sq(next.x1 - prev.x1) + sq(next.y1 - prev.y1);
    return db*sq(GLANCE_STANDARD) < min(dl, dn);
  }
  public boolean is_glance_edge(){ return is_glance_node() || (next!=null && next.is_glance_node()); }
  public void firefly(){ // unexpectedly long and complicated code for doing the animated fireflies
    if( !active ||  dist<BRIEF_MIN ){ return; }
    float SPEED = 0.02f; // controls how fast the fireflies move, in pixels per millisecond
    float line_length = sqrt(sq(x1 - xs[0]) + sq(y1 - ys[0]));
    for(int i=0;i<xs.length-1;i++){ line_length += sqrt(sq(xs[i] - xs[i+1]) + sq(ys[i] - ys[i+1])); }
    f.base.strokeWeight(2);
    for(int k=0; k<6; k++){ // the number of fireflies to draw
      float p = (millis()*SPEED  + (k*line_length/6))% line_length;
      float d = sqrt(sq(x1 - xs[0]) + sq(y1 - ys[0]));
      float xb, yb, xa, ya;
      if(p < d){ // goes in the first segment
        xb = x1; yb = y1; xa=xs[0]; ya=ys[0];
      }else{
        float summed = 0; int i=-1;
        while(i<xs.length-2 && summed+d<p){
          summed += d; i += 1; d = sqrt(sq(xs[i] - xs[i+1]) + sq(ys[i] - ys[i+1]));
        }
        p = p - summed; xb = xs[i]; yb = ys[i]; xa=xs[i+1]; ya=ys[i+1];
      }
      f.base.stroke(get_color(80)); f.base.line( ((d-p)*xb + p*xa)/d, ((d-p)*yb + p*ya)/d, ((d-p-5)*xb + (p+5)*xa)/d, ((d-p-5)*yb + (p+5)*ya)/d);
      f.base.stroke(get_color(40)); f.base.line( ((d-p)*xb + p*xa)/d, ((d-p)*yb + p*ya)/d, ((d-p+5)*xb + (p-5)*xa)/d, ((d-p+5)*yb + (p-5)*ya)/d);
    }
  }
  public void time_mark(){ // draws the small saccade marks just under the timeline
    stroke(get_color(50));fill(get_color(50));
    if(next==null){return;}
    float x = (t-f.TIME.Tmin)*TWIDTH/(f.TIME.Tmax - f.TIME.Tmin);
    float dx = (next.t - t)*TWIDTH/(f.TIME.Tmax - f.TIME.Tmin);
    if(dx < 3){ line( x+TBUFFER, height-40,  x+TBUFFER, height-20); }
    else{ noStroke();rect( x+TBUFFER, height-40, dx, 20); }
  }
}
// functionality for making and manipulating notes
class Notes{
  int note_selected = 0;
  boolean mode = false;
  ArrayList<String> notes = new ArrayList<String>();
  ArrayList<Integer> x = new ArrayList<Integer>(), y = new ArrayList<Integer>();
  Frame f;
  Notes(Frame f){this.f=f;}
  public void draw(){
    f.base.fill(white(100)); f.base.stroke(white(100)); f.base.strokeWeight(2);
    for(int i=0;i<notes.size();i++){f.base.text(notes.get(i).replace("\\n","\n"), x.get(i), y.get(i));}
  }
  public void note_click(int mX, int mY){
    for(int i=0; i<notes.size();i++){ if(notes.get(i).length()==0){notes.remove(i);x.remove(i);y.remove(i);i-=1;}} // clean up empty notes
    if(mouseButton == LEFT){ // add a new note
      mode=true;notes.add("");x.add(mX); y.add(mY); note_selected = notes.size()-1;
    }else if(mouseButton == RIGHT && notes.size()>0){
      // select closest note, and move to mouse pointer
      int closest = 0; float dist = sq(mX - x.get(0)) + sq(mY - y.get(0));
      for(int i=1;i<notes.size();i++){
        float d = sq(mX - x.get(i)) + sq(mY - y.get(i));
        if(d<dist){closest=i; dist=d;}
      }
      note_selected = closest;
      x.set(note_selected, mX); y.set(note_selected, mY);
    }
  }
  public void note_key(){
    if(key==127 && notes.size()>0){notes.remove(notes.size()-1); x.remove(x.size()-1); y.remove(y.size()-1);} // deletes last
      if(notes.size()==0){return;}
      note_selected = min(note_selected, notes.size()-1); // sanity checks
      if(key<65535 && key>31){notes.set(note_selected, notes.get(note_selected) + key);} // implements typing
      if(key==10){notes.set(note_selected, notes.get(note_selected) + "\\n");} // implements ENTER
      if(key==8 && notes.size()> 0 && notes.get(note_selected).length()>0){ // implements backspace
        notes.set(note_selected, notes.get(note_selected).substring(0, notes.get(note_selected).length()-1));
      }
  }
  
  // write and read, used for fileAccess stuff
  public void write(PrintWriter writer){
    writer.print(notes.size());writer.print(','); // first number is the length, then x,y,string ordering
    for(int i=0;i<notes.size();i++){
      writer.print(x.get(i));writer.print(',');writer.print(y.get(i));writer.print(',');writer.print(notes.get(i));writer.print(',');
    }
  }
  public void read(TableRow r1){
    int l = r1.getInt(0);
    notes = new ArrayList<String>(); x = new ArrayList<Integer>(); y = new ArrayList<Integer>();
    for(int i=0;i<l;i++){ x.add(r1.getInt(3*i+1)); y.add(r1.getInt(3*i+2)); notes.add(r1.getString(3*i+3)); }
  }
}
class Time{
  float Ts = 0; float Te = 1;
  float TTmin = 0; float TTmax = 0;
  float Tmin = 0; float Tmax = 0;
  ArrayList<Float> markers = new ArrayList<Float>();
  Time(){}
  public boolean within(float t){ return (t >= Tmin + Ts*(Tmax-Tmin)) & (t <= Tmin + Te*(Tmax-Tmin));}
  public float start(){return Tmin + Ts*(Tmax-Tmin);}
  public float end(){return Tmin + Te*(Tmax-Tmin);}
  
  public void reset(){Tmin=TTmin; Tmax=TTmax; Ts=0; Te=1;}
  public void movie_time_clicked(){MainFrame.movie_update_time = Tmin + ((mouseX-TBUFFER)/TWIDTH) * (Tmax - Tmin);}
  public String format_time(float t){return String.format("%01d", floor(t/3600)) +':'+ String.format("%02d", floor((t/60)%60)) +':'+String.format("%02d", floor(t%60));}
  public void zoom(){
    float nTmin = Tmin + Ts*(Tmax-Tmin), nTmax = Tmin + Te*(Tmax-Tmin);
    Tmin = nTmin; Tmax = nTmax; Ts=0; Te=1;
  }
  
  public void draw(){
    fill(white(100)); stroke(white(100)); strokeWeight(2);
    line(TBUFFER,height-40,TBUFFER+TWIDTH,height-40);
    arc(TBUFFER + Ts*TWIDTH, height-40, 30, 30, HALF_PI, 3*HALF_PI);
    arc(TBUFFER + Te*TWIDTH, height-40, 30, 30, -HALF_PI, HALF_PI);
    float tts = Tmin + Ts*(Tmax-Tmin); float tte = Tmin + Te*(Tmax-Tmin);
    text(format_time(tts), 10 + Ts*TWIDTH, height-10);
    text(format_time(tte), 50 + Te*TWIDTH, height-10);
    // add marker for movie time
    if(MainFrame.clip!=null){
      float tc = MainFrame.clip.time();
      strokeWeight(1);stroke(white(100));
      float x = (tc-Tmin)/(Tmax - Tmin);
      line( x*TWIDTH+TBUFFER, height-20,  x*TWIDTH+TBUFFER, height-60);
    }
    // add user defined marks
    stroke(o1(70));strokeWeight(2);
    for(float i : markers){
      float c = (i-Tmin)/(Tmax - Tmin)*TWIDTH+TBUFFER;
      line(c, height-100, c, height-20);
    }
  }
  public void clicked(){
    if(mouseButton==LEFT){ // move the closest time bound
      float q = max(0, min(1, (mouseX - TBUFFER)/TWIDTH));
      if( q < (Ts+Te)/2 ){Ts = q;}else{Te=q;}
    }else if(mouseButton==RIGHT){ // draw or delete a marker
      float clicked_time = Tmin + (Tmax-Tmin)*(mouseX-TBUFFER)/TWIDTH;
      int closest = 0; float mindist = abs(Tmax-Tmin);
      for(int i=0;i<markers.size();i++){
        float newdist = abs(markers.get(i)-clicked_time);;
        if(newdist < mindist){closest=i;mindist=newdist;}
      }
      if(mindist > 0.01f*abs(Tmax-Tmin)){ markers.add(clicked_time); }
      else{ markers.remove(closest); }
    }
  }
}
// controls for user inputs (keys or mouse pressed, mousewheel, etc.)

public void keyPressed() {
  // note making controls
  if (MainFrame.notes.mode) {
    MainFrame.notes.note_key(); 
    return;
  }
  else if (key == 's') { saveStuff(); }
  // ZOOMING
  else if (key == '+') { 
    if (mouseY<height-100) {
      z*= 1.2f;
    } else {
      MainFrame.TIME.zoom();
    }
  } else if (key == '-') { 
    if (mouseY<height-100) {
      z = 1;
    } else {
      MainFrame.TIME.reset();
    }
  } else if ( (key==',' || key==127) && MainFrame.FILTER.Lenses.size()>0) { // ',' or delete to remove selected lens
    MainFrame.FILTER.Lenses.remove(MainFrame.FILTER.selected);
    MainFrame.FILTER.selected = max(MainFrame.FILTER.selected-1, 0);
  } else if ( (key=='.' || key==10) && MainFrame.FILTER.Lenses.size()<MAX_LENSES) { // '.' or enter to add a new lens
    MainFrame.FILTER.Lenses.add(new Lens(MainFrame));
    MainFrame.FILTER.selected = MainFrame.FILTER.Lenses.size()-1; 
    MainFrame.FILTER.selected_time=millis();
    MainFrame.FILTER.update_all();
  }
  else if(key=='1'){ selected_frame = 0; }
  else if(key=='2'){ selected_frame = 1; }
  else if(key=='3'){ selected_frame = 2; }
  else if(key=='4'){ selected_frame = 3; }
  else if(key=='0'){ show_all^=true;}
  // special command for applying the dynamic bundling
  else if (key=='7') { MainFrame.bundle_manager(1); }
  else if (key=='8') { MainFrame.bundle_manager(2); }
  else if (key=='9') { MainFrame.bundle_manager(3); }
  Frames[selected_frame].lower.has_changed = true;

  // backspace, deletes the closest node
  if ( key==BACKSPACE && MainFrame.nodes.size()>0 && !is_bundling) { // deletes the closest node, can't be done during bundling since it would corrupt the data
    int found = 0;
    float min_dist = sq(MainFrame.nodes.get(0).x - mouseX) + sq(MainFrame.nodes.get(0).y - mouseY);
    for (int i=1; i<MainFrame.nodes.size(); i++) {
      if (!MainFrame.TIME.within(MainFrame.nodes.get(i).t)) {
        continue;
      } // node is hidden by the time filter, so skip it
      float new_dist = sq(MainFrame.nodes.get(i).x - mouseX) + sq(MainFrame.nodes.get(i).y - mouseY);
      if (new_dist<min_dist) {
        found=i;
        min_dist=new_dist;
      }
    }
    Node f = MainFrame.nodes.get(found);
    if (f.LastNode != null) {
      f.LastNode.NextNode = f.NextNode;
    }
    if (f.NextNode != null) {
      f.NextNode.LastNode = f.LastNode;
    }
    MainFrame.nodes.remove(found);
    if (MainFrame.node_curves.size()>0) {
      if (found == MainFrame.node_curves.size()) {
        MainFrame.node_curves.remove(found-1);
      } // last guy, so just remove the edge going to it
      else {
        MainFrame.node_curves.remove(found);
      }
    }
    MainFrame.lower.has_changed=true;
  }

  //// quickstart : only really intended for development use, can remove later
  if (key=='q') { // DEV feature to load some files quickly, so I don't have to do it myself
    MainFrame.bgi = loadImage("q.png");
    MainFrame.fx = PApplet.parseFloat(MainFrame.lower.base.width)/(MainFrame.bgi.width); 
    MainFrame.fy = PApplet.parseFloat(MainFrame.lower.base.height)/(MainFrame.bgi.height);
    MainFrame.bgi.resize(MainFrame.lower.base.width, MainFrame.lower.base.height);
    attach("q2.csv"); 
    MainFrame.process(); 
    MainFrame.TIME.reset();
  }
}

public void mouseWheel(MouseEvent event) {
  if (controller.isOpen &&mouseY < height-100 && mouseX > width-150) {
    controller.mouseWheel(event);
  } // controller gets to handle this
  else if (mouseY<height-100 && !MainFrame.notes.mode) {
    MainFrame.FILTER.adjust(event);
  }
  MainFrame.lower.has_changed = true;
}
public void mouseDragged() {
  mouseClicked();
}
public void mouseClicked() {
  if (mouseY > height-100) { // lower control panel gets to handle this
    if (mouseX > width-60) {
      controller.isOpen = !controller.isOpen; 
      return;
    } // swap the controls window
    else if (CLIP_MODE && (mouseX - TBUFFER)/TWIDTH  > MainFrame.TIME.Ts && (mouseX - TBUFFER)/TWIDTH < MainFrame.TIME.Te ) {
      MainFrame.TIME.movie_time_clicked();
    } else {
      MainFrame.TIME.clicked();
    }
  } else if (mouseY < 900 && mouseX<1700) {
    FrameClicked();
  } // mouse is pressed on screen, so update the lenses
  MainFrame.lower.has_changed = true;
}
public void mousePressed() { 
  if (mouseButton==LEFT) { lmp=true; }
  else if (mouseButton==RIGHT) { rmp=true; }
  if (controller.isOpen && mouseY<height-100 && mouseX > width-150) {
    controller.clicked();
  } // controller gets to handle this
}
public void mouseReleased() { lmp = false; rmp = false; }

public void FrameClicked(){
  if(!show_all){
    if(MainFrame.notes.mode){MainFrame.notes.note_click(mouseX, mouseY);}else{MainFrame.FILTER.move(mouseX, mouseY);}
  }else{
    int c = 0;
    if(mouseY > MainFrame.base.height/2){c+=2;}
    if(mouseX > MainFrame.base.width/2){c+=1;}
    if(c != selected_frame && mouseButton==RIGHT){
      Frames[selected_frame].FILTER.apply_copy(Frames[c]); return;
    }else if(c != selected_frame && mouseButton==LEFT){
      selected_frame = c; MainFrame = Frames[selected_frame];
      if(mouseButton==RIGHT){return;}
    }
    int mX = 2*(mouseX % (MainFrame.base.width/2));
    int mY = 2*(mouseY % (MainFrame.base.height/2));
    if(MainFrame.notes.mode){MainFrame.notes.note_click(mX,mY);}else{MainFrame.FILTER.move(mX, mY);}
  }
  
}
// functionality for loading from and saving to files
Table table;

public void openStuff(){
  try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) { e.printStackTrace(); }
  fileChooser.updateUI();
  JDialog fileWrapper = new JDialog(); fileWrapper.setVisible(false); fileWrapper.setAlwaysOnTop(true);
  int result = fileChooser.showOpenDialog(fileWrapper);
  if(result != JFileChooser.APPROVE_OPTION){return;}
  String selectedFile = fileChooser.getSelectedFile().getAbsolutePath();
  println(selectedFile);
  if(selectedFile.toLowerCase().endsWith(".png")) { // makes the background image
    MainFrame.bgi = loadImage(selectedFile);
    MainFrame.fx = PApplet.parseFloat(MainFrame.lower.base.width)/MainFrame.bgi.width; MainFrame.fy = PApplet.parseFloat(MainFrame.lower.base.height)/MainFrame.bgi.height;
    MainFrame.bgi.resize(MainFrame.lower.base.width, MainFrame.lower.base.height);
  }
  else if(selectedFile.toLowerCase().endsWith(".bundle")){ loadBundle(selectedFile); MainFrame.TIME.reset(); }
  else if(selectedFile.toLowerCase().endsWith(".setting")){ loadSetting(selectedFile); check_setting(selectedFile); }
  else if(selectedFile.toLowerCase().endsWith(".mp4")){ MainFrame.clip = new Movie(this, selectedFile); }
  else if(selectedFile.toLowerCase().endsWith(".csv")){ attach(selectedFile); MainFrame.process(); MainFrame.TIME.reset();}
  // ... other valid input files to consider?
  println("done opening", selectedFile);
}
public void saveStuff(){
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

public void check_setting(String filename){
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

public void attach(String name){
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

public void saveBundle(String selectedFile){
  PrintWriter writer;
  try{ writer = new PrintWriter(selectedFile, "UTF-8"); }
  catch (Exception e) { e.printStackTrace(); return; }
  writer.println("x,y,l,t,dt");
  // x, y, l, t, dt (last two zero except at locked nodes?)
  for(int j=0;j<MainFrame.node_curves.size();j++){
    Node nv = MainFrame.nodes.get(j);
    NodeCurve n = MainFrame.node_curves.get(j);
    printrow(writer, n.x1, n.y1, 1, nv.t, nv.dt, n.type);
    for(int i=0; i<n.xs.length;i++){
      printrow(writer, n.xs[i], n.ys[i], 0,0,0,0);
    }
  }
  Node n = MainFrame.nodes.get(MainFrame.nodes.size()-1); // final node to close the bundle
  printrow(writer, n.x, n.y, 1, n.t, n.dt, n.type);
  writer.close();
  println("Writer finished writing", writer, selectedFile);
}
public void printrow(PrintWriter w, float x, float y, float l, float t, float dt, float type){
  for(float i : new float[] {x,y,l,t,dt}){w.print(i);w.print(',');}
  w.print(type);w.print('\n');
}
public void loadBundle(String bundlePath){
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



public void saveSetting(String selectedFile){
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
public void loadSetting(String settingPath){
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

public void export_timeline(String selectedFile){
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
  public void settings() {  size(1700, 1000, P2D); }
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "VETA" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
