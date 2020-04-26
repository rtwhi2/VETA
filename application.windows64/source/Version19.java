import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import processing.video.*; 
import com.nativelibs4java.opencl.*; 
import com.nativelibs4java.opencl.CLMem.*; 
import java.nio.ByteOrder; 
import static java.lang.System.*; 
import static org.bridj.Pointer.*; 
import java.io.File; 
import javax.swing.*; 

import com.nativelibs4java.opencl.util.fft.*; 
import com.nativelibs4java.opencl.util.*; 
import com.nativelibs4java.opencl.*; 
import com.ochafik.util.string.*; 
import com.nativelibs4java.opencl.library.*; 
import org.bridj.*; 
import org.bridj.ann.*; 
import org.bridj.cpp.com.*; 
import org.bridj.cpp.com.shell.*; 
import org.bridj.cpp.*; 
import org.bridj.cpp.mfc.*; 
import org.bridj.cpp.std.*; 
import org.bridj.cs.*; 
import org.bridj.cs.dotnet.*; 
import org.bridj.cs.mono.*; 
import org.bridj.demangling.*; 
import org.bridj.dyncall.*; 
import org.bridj.func.*; 
import org.bridj.jawt.*; 
import org.bridj.objc.*; 
import org.bridj.util.*; 
import org.bridj.relocated.org.objectweb.asm.*; 
import org.bridj.relocated.org.objectweb.asm.signature.*; 
import com.nativelibs4java.util.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class Version19 extends PApplet {







// file selection
 

JFileChooser fileChooser = new JFileChooser();

// background image information
PImage bgi;
float fx=0, fy=0; // resizing ratios. Needed for resizing the node lists using the bgi size
// node and bundle information
Table table;
ArrayList<Node> nodes = new ArrayList<Node>();
ArrayList<NodeCurve> node_curves = new ArrayList<NodeCurve>();
// optional video information
Movie clip;
PImage clip_frame;
boolean CLIP_MODE = false; // toggles showing the video
float movie_update_time=0, movie_start=0;
// capsules for some of the filtering and optimised graphics behaviour
Filter FILTER = new Filter(); Time TIME = new Time(); Notes notes = new Notes();
LowerLayer lower; ControlWindow controller;
// constant numerics, not user controllable
float SIZE = 10; // general factor for symbol sizes
float R = 0.3f; //reduced size inside the len
float GLANCE_STANDARD = 3; // how much closer the before and after must be, compared to the middle, to call it a glance
int MAX_LENSES = 10; // limit for the number of lenses allowed
// Side threads for bundling data, and updating edge acceptance
volatile boolean is_bundling = false, is_activating = false;
// user controls features
boolean SHOW_SHORT = true, SHOW_GLANCE = false, SHOW_BASIC = false, SHOW_DOTS = true; // lower layer show/hide toggles
boolean GENERAL = true, ALTERNATE = false, USE_SIZE = true, ANIMATE = false;  // foreground modes, whether to use dt for sizing, whether to use animated saccades
boolean INTERLACE = false; // foreground alternate view interlaced lines option setting
boolean HIST = false; float swap_time=-2000; // shows the location-time pairing lines. ( original suggestion was a sliding animation, might do later )
boolean BUNDLE = false; // toggles continuous bundling
boolean TRAVEL_LINES = false; // draw the extra 'travel' lines between before and after nodes
float z=1; // degree of display zooming
float ALT_TIME_WINDOW = 10; // number of seconds to look around an observation in the alternative view
float BRIEF_MIN = 100; // length below which saccades are classed as short
int COL_MODE = 0; // 0 = no colouring, 1 = by type, 2 = by angle (effects bundling rules similarly)
float BIG_K = 0.05f; // 0.05 is I think an appropriate choice, until I next modify the other bundling parameters. But for more crowded screens, higher is needed.
boolean lmp = false, rmp = false; // (track whether left or right mouse is currently held down)

//Colouring and other graphical features
public int c1(float a){return color(17,100, 80,a);} // glances - background
public int c2(float a){return color(50,100, 80,a);} // normal - background
public int c3(float a){return color(70,100,100,a);} // close - background
public int o1(float a){return color( 0, 60, 60,a);} // before - foreground
public int o2(float a){return color(17, 60,100,a);} // glances - foreground
public int o3(float a){return color(40,100, 80,a);} // after - foreground
public int white(float a){return color(0, 0,100, a);} // plain white
public int  grey(float a){return color(0, 0, 50, a);} // plain grey, used for out-of-filter time marks
public int black(float a){return color(0, 0,  0, a);} // plain black
int[] sws = new int[] {2,4,8,12},  divs = new int[] {1,6,10,14}; // used in the node_curve alpha edge splattering
PFont  f; // the font used for general text writing applications. defined in setup

// objects needed for GPU based bundling processing (initialised by the GPUsetup function)
String src_cl;
CLContext context; CLQueue queue; ByteOrder byteOrder;
CLProgram program; CLKernel inner, outer, inner_directed, current;
Pointer<Float> xPtr, yPtr, lPtr, fPtr;
CLBuffer<Float> xb, yb, lb, fb;
//CLEvent addEvt;
int current_buffer_size = 0;
boolean GPU_setup = false; // boolean describing if the GPU context information has been set up yet

public void setup(){
  
  surface.setTitle("Viz");
  lower = new LowerLayer(); controller = new ControlWindow();
  colorMode(HSB, 100);
  f = createFont("Arial",12,true);
  println("ready", sketchFile(""), height, width);
  //GPU setup safety catch, needed for handling the demon bug on my device, probably isn't needed elsewhere
  thread("GPUsetup"); // tries its best to setup the openCL behaviour, but can freeze due to the demon bug, so threaded
}
public void draw(){
  background(black(100)); //print(frameRate, ' ');
  if(bgi==null){ // user needs to load a background image before we can even begin.
    fill(white(100)); textFont(f);
    text(  String.join("\n", loadStrings("startmess.txt")), width/2 - 200, height/2 - 50 );
  }else if(CLIP_MODE && clip!=null && clip.available()){//draw the movie screen
    if(movie_update_time != 0){clip.jump(movie_update_time);movie_update_time=0;}
    if( clip.time() < TIME.start() || clip.time() > TIME.end() ){clip.jump(TIME.start());}
    clip.read();
    clip_frame = clip.get();
    clip_frame.resize(lower.base.width, lower.base.height);
    float f = min(100, (millis()-movie_start)/50);
    if(f<100){image(lower.base, 0, 0);}
    tint(100, f);
    image(clip_frame, 0, 0);
    noTint();
  }else{ // main mode
    // do bundling thread if needed and not in progress, then do update thread (if anything has changed) and rebuild the lower layer
    if(BUNDLE && !is_bundling){thread("autobundle");} // bundling might take multiple frames per round, but we still want a fast foreground, so it gets a thread
    else if(!BUNDLE && is_bundling){is_bundling=false;} // current thread will either terminate or freeze, but we still want to be able to make new ones
    // update the edge acceptance information (threaded for a bit more speed), then rebuild and print the lower component
    if( z!=1 && (pmouseX!=mouseX || pmouseY!=mouseY) ){lower.has_changed=true;} // if we moved the mouse while zoomed, need to update
    if(!is_activating && lower.has_changed){thread("update_active");lower.build();} // first condition will almost never fail, don't worry about it
    image(lower.base,0,0);
    
    // draw the foreground features (animation, connecting symbols, etc., quick to draw and needing regular updating)
    if(z!=1){translate(-mouseX*(z-1), -min(mouseY,height-100)*(z-1));scale(z);} // zoom for the foreground components is handled here
    if(ALTERNATE){FILTER.foreground(false);}
    if(GENERAL){FILTER.foreground(true);}
    if(ANIMATE){for(NodeCurve n : node_curves){n.firefly();}}
    FILTER.draw(); // draw the Filter lens circles
    if(z!=1){scale(1/z);translate(mouseX*(z-1), min(mouseY,height-100)*(z-1));} // rest of the components are the interface, don't need to have zooming
    for(NodeCurve n : node_curves){if(n.active){n.time_mark();}} // time marks hide behind the timeline
    notes.draw(); // draw notes
  }
  
  // control and timeline components should be visible in any state
  FILTER.filtered_timeline(); TIME.draw(); // draw the timelines
  controller.draw(); // should exist even in movie mode, since it has the button
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
    if(HIST)           {fill(k1);rect(wx    ,wy+150, 50, 50); fill(k2);text("Time\nHist",          wx+  2,wy+165);}
    else{               fill(k2);rect(wx    ,wy+150, 50, 50); fill(k1);text("Time\nHist",          wx+  2,wy+165);}
    if(BUNDLE)         {fill(k1);rect(wx+ 50,wy+150, 50, 50); fill(k2);text("Cont.\nBundle",       wx+ 52,wy+165);}
    else if(!GPU_setup){fill(0 );rect(wx+ 50,wy+150, 50, 50); fill(0 );text("Cont.\nBundle",       wx+ 52,wy+165);} // grey out since it is disabled
    else{               fill(k2);rect(wx+ 50,wy+150, 50, 50); fill(k1);text("Cont.\nBundle",       wx+ 52,wy+165);}
                        fill(k2);rect(wx+100,wy+150, 50, 50); fill(k1);text("Clear\nBundle",       wx+102,wy+165); // no conditional, just have as black
    // last row, might not need it all
    if(notes.mode)  {fill(k1);rect(wx    ,wy+200, 50, 50); fill(k2);text("add notes",           wx+  2,wy+215);}
    else{            fill(k2);rect(wx    ,wy+200, 50, 50); fill(k1);text("add notes",           wx+  2,wy+215);}
    if(TRAVEL_LINES){fill(k1);rect(wx+ 50,wy+200, 50, 50); fill(k2);text("Travel\nLines",       wx+ 52,wy+215);}
    else{            fill(k2);rect(wx+ 50,wy+200, 50, 50); fill(k1);text("Travel\nLines",       wx+ 52,wy+215);}
    if(ANIMATE)     {fill(k1);rect(wx+100,wy+200, 50, 50); fill(k2);text("Animate",             wx+102,wy+215);}
    else{            fill(k2);rect(wx+100,wy+200, 50, 50); fill(k1);text("Animate",             wx+102,wy+215);}
    
    // 1: COL_MODE, FILTER_MODE, ALT_TIME_WINDOW
    fill(k2);rect(wx    , wy+250, 50, 50); fill(k1);text("ColMode\n " + String.format("%d", COL_MODE),         wx+  2,wy+275);
    fill(k2);rect(wx+ 50, wy+250, 50, 50); fill(k1);text("FltrMode\n "+ String.format("%d", FILTER.mode),      wx+ 52,wy+275);
    fill(k2);rect(wx+100, wy+250, 50, 50); fill(k1);text("Alt Time\n "+ String.format("%.0f", ALT_TIME_WINDOW),wx+102,wy+275);
    // 2: Big_K, Brief_min
    fill(k2);rect(wx    , wy+300, 50, 50); fill(k1);text("K:\n "      + String.format("%.3f", BIG_K),          wx+  2,wy+325);
    fill(k2);rect(wx+ 50, wy+300, 50, 50); fill(k1);text("MinDist\n " + String.format("%.0f", BRIEF_MIN),      wx+ 52,wy+325);
    //fill(k2);rect(wx+100, wy+50, 50, 50); fill(k1);text("Alt Time\n "+ String.format("%.0f", ALT_TIME_WINDOW),wx+102,wy+75);
    
    // settings menu, 3 guys tall
    wy = height - 150 - 300; // miniscreens will be 150px wide, 100px tall
    if(screens.size()>3){text(String.format("%d / %d", view_place, screens.size()), wx+10, wy-20);}
    for(int i=view_place;i < min(view_place+3, screens.size());i++){
      image(screens.get(i), wx, wy + 100*(i-view_place));
    }
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
      else if(width-mouseX >   0){CLIP_MODE^=true; if(clip!=null){if(CLIP_MODE){clip.loop();}else{clip.pause();}} movie_start=millis();}
    }else if(mouseY < 200){ // Fourth Row
      if     (width-mouseX > 100){HIST^=true; swap_time=millis();}
      else if(width-mouseX >  50){BUNDLE^=GPU_setup;}
      else if(width-mouseX >   0){process();;}
    }else if(mouseY < 250){ // Fifth Row, probably last
      if     (width-mouseX > 100){notes.mode^=true;}
      else if(width-mouseX >  50){TRAVEL_LINES^=true;}
      else if(width-mouseX >   0){ANIMATE^=true;}
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
        if     (width-mouseX > 100){COL_MODE = (3+COL_MODE + event.getCount())%3; for(NodeCurve n : node_curves){n.apply_color();} }
        else if(width-mouseX >  50){FILTER.mode = (5+FILTER.mode + event.getCount())%5;}
        else if(width-mouseX >   0){ALT_TIME_WINDOW = max(0, min(60, ALT_TIME_WINDOW + 1*event.getCount()));}
    }else if(mouseY > 300 && mouseY < 350){ // second scroll row
        if     (width-mouseX > 100){ BIG_K = min(100.0f, max(0.001f, BIG_K * exp(0.2f*event.getCount())));}
        else if(width-mouseX >  50){ // BRIEF MIN
          float NEW_BRIEF_MIN = max(0.4f, min(2000, BRIEF_MIN * pow(1.1f,event.getCount())));
          for(NodeCurve n : node_curves){ if(n.dist<BRIEF_MIN != n.dist<NEW_BRIEF_MIN){n.apply_color();} }
          BRIEF_MIN = NEW_BRIEF_MIN;
        }
        else if(width-mouseX >   0){} // not currently used
    }else if(mouseY > height-450 && mouseY < height-150){ // we are in the miniscreens bar
      view_place = max(min(view_place + event.getCount(), screens.size()-3), 0); // scroll through, limited to a range
    }
  }
}
class Filter{ // capsule for all the filtering behavior
  ArrayList<Lens> Lenses;
  int selected = 0, selected_time = 0;
  int mode = 0; // numeric 0-4, decides what filtering to do to saccades between potentially multiple lenses (and also the foreground fixations)
  Filter(){ Lenses = new ArrayList<Lens>(); }
  
  public void draw(){
    for(int i=0;i<Lenses.size();i++){Lenses.get(i).draw(i==selected);}
    if(Lenses.size()>0){ // draw the line marking the selected lens
      stroke(grey(50)); strokeWeight(2);
      float h = height - 100 + (60.0f*selected + 30)/Lenses.size();
      line(100, h, width-100, h);
    }
  }
  
  public void update_all(){
    for(Lens l : Lenses){l.update(l.mX, l.mY);}
    for(NodeCurve n : node_curves){n.altt=true;}
  }
  public void move(){
    if(Lenses.size()>0){
      int current = 0;
      if(Lenses.get(selected).inside(mouseX, mouseY)){
        current=selected;
      }else{
        float mindist = sq(mouseX-Lenses.get(0).mX) + sq(mouseY-Lenses.get(0).mY);
        float newdist = 0;
        for(int i=1;i<Lenses.size();i++){
          newdist = sq(mouseX-Lenses.get(i).mX) + sq(mouseY-Lenses.get(i).mY);
          if(newdist < mindist){mindist=newdist;current=i;}
        }
      }
      if(current!=selected){selected=current;selected_time=millis();}
      else if(millis() - selected_time > 150){Lenses.get(selected).update(mouseX,mouseY);}
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
    if( !TIME.within(G.t) ){return false;} // no drawing, since out of time frame
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
    ArrayList<Integer> inst = new ArrayList<Integer>();
    for(int i=0;i<nodes.size();i++){
      ArrayList<Integer> results = new ArrayList<Integer>();
      for(int k=0;k<Lenses.size();k++){
        if(isgen){results.add(nodes.get(i).general_value(Lenses.get(k)));}
        else{results.add(nodes.get(i).alternate_value(Lenses.get(k)));}
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
    if(HIST){ tval = min(1, (millis()-swap_time)/1000); } else { tval = 1 - min(1, (millis()-swap_time)/1000); }
    if(tval>0){
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
    }else{ // draw normally
      for(int i=0;i<nodes.size();i++){
        if(isgen){ nodes.get(i).draw_self(inst.get(i), 0); }
        else{ nodes.get(i).draw_self(inst.get(i), 1); }
        if(TRAVEL_LINES && inst.get(i)==3 && inst.get(i+1)==1){ // the Travel lines
          int k = i+1; strokeWeight(2);
          while(k<nodes.size() && (inst.get(k)==1 || inst.get(k)==2) ){k++;}
          if(k<nodes.size() && inst.get(k)==4){
            Node ni = nodes.get(i); Node nk = nodes.get(k);
            stroke(o1(60)); line(ni.x, ni.y, (ni.x+nk.x)/2, (ni.y+nk.y)/2);
            stroke(o3(60)); line(nk.x, nk.y, (ni.x+nk.x)/2, (ni.y+nk.y)/2);
          }
        }
      }
    }
  }
  public void filtered_timeline(){
    strokeWeight(1);
    if(Lenses.size()==0 || (!ALTERNATE&&!GENERAL) ){ // degenerate case, just make grey marks
      for(int i=0;i<nodes.size();i++){
        nodes.get(i).time_mark(0, 0, 1);
      }
    }else if(ALTERNATE){ // use the alternate valuation
      for(int k=0;k<Lenses.size();k++){
        for(int i=0;i<nodes.size();i++){
          nodes.get(i).time_mark(nodes.get(i).alternate_value(Lenses.get(k)), k, Lenses.size());
        }
      }
    }else{ // use the general valuation
      for(int k=0;k<Lenses.size();k++){
        for(int i=0;i<nodes.size();i++){
          nodes.get(i).time_mark(nodes.get(i).general_value(Lenses.get(k)), k, Lenses.size());
        }
      }
    }
  }
}
class Lens{
  // gonna need to make lenses carry their own paraproperties before they can have multiple. Which means I need a good way of resizing?
  float WINDOW = 400; // the general size of the window
  float FACTOR = 0; // the ovalness of the window
  float LS=2; // the p-shape of the lens

  float mX=300; float mY=300;
  float mXt=2*300/WINDOW; float mYt=2*300/WINDOW;
  
  Lens(){
    mX = min(max(mouseX,0), width); mY = min(max(mouseY,0), height-100);
    update(mX,mY);
  }
  
  public void update(float x_, float y_){ mX=x_; mY=y_; mXt = (2*x_/exp(-FACTOR))/WINDOW; mYt = (2*y_/exp(FACTOR))/WINDOW;}
  
  public void draw(boolean selected){
    noFill();
    stroke(white(100));
    strokeWeight(1);
    if(selected){strokeWeight(3);}
    float fX = exp(-FACTOR)*WINDOW/2, fY = exp(FACTOR)*WINDOW/2;
    if(LS==32.0f){ rect(mX-fX, mY-fY, 2*fX, 2*fY);return; } // much easier to draw a rectangle
    beginShape(); // inb4 this is bad coding practice
    for(float i=-0.5f;i<0.5f;i=i+0.02f){vertex(mX+fX*(i/abs(i))*pow(  abs(i), 1/LS),mY+fY*           pow(1-abs(i), 1/LS));}
    for(float i=-0.5f;i<0.5f;i=i+0.02f){vertex(mX+fX*           pow(1-abs(i), 1/LS),mY-fY*(i/abs(i))*pow(  abs(i), 1/LS));}
    for(float i=-0.5f;i<0.5f;i=i+0.02f){vertex(mX-fX*(i/abs(i))*pow(  abs(i), 1/LS),mY-fY*           pow(1-abs(i), 1/LS));}
    for(float i=-0.5f;i<0.5f;i=i+0.02f){vertex(mX-fX*           pow(1-abs(i), 1/LS),mY+fY*(i/abs(i))*pow(  abs(i), 1/LS));}
    endShape(CLOSE);
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
    //x1 = (x1-mXt); x2 = (x2-mXt); //transform to make window a 1x1 p-circle centered at zero
    //y1 = (y1-mYt); y2 = (y2-mYt);
    
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
    if(mousePressed && rmp){ // right mouse key adjustment
      LS = LS*exp(-log(2)*event.getCount()/3); // modify shape
      LS = min(32, max(1, LS));
    }else if(mousePressed && lmp){ // left mouse key adjustment
      WINDOW = WINDOW - (5 + WINDOW/15)*event.getCount(); // modify size
      WINDOW = min(2000*exp(abs(FACTOR)), max(15, WINDOW));
    }else{ // no mouse key adjustment
      FACTOR = FACTOR - 0.2f*event.getCount(); // modify squish
      FACTOR = min(5, max(-5, FACTOR));
    }
    update(mX, mY);
  }
}
class LowerLayer{
  PGraphics base;
  PImage bgr = null;
  boolean has_changed = true;
  LowerLayer(){base=createGraphics(1700, 900, P2D);}
  
  // builds most of image, all except interface and foreground features that aren't very time-consuming to draw
  public void build(){
    float start_t = millis();
    has_changed = false; // do at the start, in case it changes again before we are done
    base.noSmooth();
    base.beginDraw();
    if(z!=1){ // resizes the background image if we have zoomed in
      bgr = bgi.copy();
      bgr.resize(floor(z*bgi.width),0);
      bgr = bgr.get(floor(min(mouseX,base.width)*(z-1)), floor(min(mouseY,base.height)*(z-1)), base.width, base.height);
      base.background(bgr);
    }
    else{base.background(bgi);}
    // draw a darker filter over the background, to make the actual data more clear on top of it
    base.noStroke(); base.fill(0,0,0,200); base.rect(0,0,base.width,base.height);
    // apply zooming for background features
    if(z!=1){base.translate(-mouseX*(z-1), -min(mouseY,height-100)*(z-1));base.scale(z);}
    // shows the background fixation points
    if(SHOW_DOTS){for(Node n : nodes){ // does the background nodes
      if(TIME.within(n.t)){
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
    for(NodeCurve n : node_curves){
      if( n.active && n.dist<BRIEF_MIN ){ // draws the short saccades first, so that they can form a background for context
        base.stroke(c3(25));base.strokeWeight(2);
        base.line(n.x1, n.y1, n.xs[n.xs.length-1], n.ys[n.ys.length-1]);
      }
    }
    if(!ANIMATE){
      for(NodeCurve n : node_curves){ // then draws the longer saccades on top of them
        if( n.active && n.dist>=BRIEF_MIN ){
          for(PShape i : n.content){base.shape(i);}
        }
      }
    }
    base.endDraw();
    println("lower layer in ", millis()-start_t);
  }
}

// updates the node information before the lower layer uses it, runs in its own thread
public void update_active(){
  float start_t = millis();
  is_activating=true;
  for(NodeCurve n: node_curves){
    if(n.altc){n.update_content(); n.update_t(); lower.has_changed = true;}
    if(n.altt){n.update_t();}
    boolean g = FILTER.can_draw_curve(n);
    if(n.active != g){n.active=g; lower.has_changed=true;}
  }
  is_activating=false;
    println("updated in ", millis()-start_t);
}
class Node{
  float t,x,y,dt;
  Node NextNode = null;
  Node LastNode = null;
  Node(float t_, float x_, float y_, float dt_){ t=t_;x=x_;y=y_;dt=dt_; }
  public void next_node(Node next_node){ NextNode = next_node; }
  public void last_node(Node last_node){ LastNode = last_node; }
  
  public int general_value(Lens LENS){ // evaluates the nodes type in relation to a given lens, in the Generic mode
    if( !TIME.within(t) ){return 0;} // greyed out, since out of reference frame
    if(LENS.inside(x,y)){return 1;} // white, since in the box
    boolean BEF = LastNode!=null && LENS.inside(LastNode.x, LastNode.y), AFT = NextNode!=null && LENS.inside(NextNode.x, NextNode.y);
    if(BEF & AFT){return 2;}
    if(BEF){return 4;}
    if(AFT){return 3;}
    return 0; // greyed out, since no interesting behaviour with this lens
  }
  public int alternate_value(Lens LENS){ // evaluates the nodes type in relation to a given lens, in the Alternate mode
    if( !TIME.within(t) ){return 0;} // greyed out, since out of reference frame
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
    else if(inst==1){fill(white(100));stroke(white(100));}//white
    else if(inst==2){fill(o2(80));stroke(o2(80));}//orange
    else if(inst==3){fill(o1(80));stroke(o1(80));}//red
    else if(inst==4){fill(o3(80));stroke(o3(80));}//green
    
    if(INTERLACE & ALTERNATE & symbol==1){//draw in the interlacing lines
      strokeWeight(2);
      if( (inst==3 || inst==2) && NextNode!=null ){ line(x,y,NextNode.x,NextNode.y); }
      if( (inst==4 || inst==2) && LastNode!=null ){ line(x,y,LastNode.x,LastNode.y); }
    }
    float s = SIZE;
    if(USE_SIZE){s = s*sqrt(dt);}
    if(sq(mouseX-x)+sq(mouseY-y)<sq(s/2)){ line(x,y,100+(width-200)*(t-TIME.Tmin)/(TIME.Tmax-TIME.Tmin),height-100); } // timeline connector
    if(inst==1){s = s*R;}
    if(symbol==0){ noStroke();ellipse(x,y,s,s); }
    else if(symbol==1){ // draws an X symbol
      strokeWeight(3); strokeCap(SQUARE);
      line(x-s/2, y-s/2, x+s/2, y+s/2);
      line(x-s/2, y+s/2, x+s/2, y-s/2);
    }
  }
  public void time_mark(int inst, int level, int num_levels){
    if(inst==0){stroke(grey(50));fill(grey(50));}//greyed out
    else if(inst==1){fill(white(100));stroke(white(100));}//white
    else if(inst==2){fill(o2(80));stroke(o2(80));}//orange
    else if(inst==3){fill(o1(80));stroke(o1(80));}//red
    else if(inst==4){fill(o3(80));stroke(o3(80));}//green
    
    float vt = (t-TIME.Tmin)*(width-200)/(TIME.Tmax - TIME.Tmin);
    float t_width = dt*(width-200)/(TIME.Tmax - TIME.Tmin);
    float lower = height-100 + (60.0f*level)/num_levels;
    float upper = lower + 60.0f/num_levels;
    if(t_width < 2){ line( vt+100, lower,  vt+100, upper); } // draw timemark as a line
    else{ noStroke(); rect( vt+100, lower,  t_width, 60.0f/num_levels ); } // draw timemark as a rect, since it is wide
    //if( TIME_PAIRS && level==FILTER.selected && inst>1 ){ line( vt+100, lower, this.x, this.y ); } // connects timemark and onscreen node
  }
  public void t_animate_mark(int inst, int symbol, float level, float at, int BINS){
    if(inst==0){return;}//greyed out
    else if(inst==1){fill(white(100));stroke(white(100));}//white
    else if(inst==2){fill(o2(80));stroke(o2(80));}//orange
    else if(inst==3){fill(o1(80));stroke(o1(80));}//red
    else if(inst==4){fill(o3(80));stroke(o3(80));}//green
    
    float td = (t-TIME.Tmin)/(TIME.Tmax-TIME.Tmin);
    int bin = floor(BINS*td); println(bin, td, TIME.Tmin, t, TIME.Tmax);
    float tx = 100 + (bin+0.5f)/BINS*(width-200); float ty = height-110 - level;
    float tt = min(1, max(0, 5*at - 4*td ));
    float ix = tt*tx + (1-tt)*x, iy = tt*ty + (1-tt)*y;
    float s = SIZE;
    if(USE_SIZE){s = s*sqrt(dt);}
    if(inst==1){s = s*R;}
    if(symbol==0){ noStroke();ellipse(ix,iy,s,s); }
    else if(symbol==1){ // draws an X symbol
      strokeWeight(3); strokeCap(SQUARE);
      line(ix-s/2, iy-s/2, ix+s/2, iy+s/2);
      line(ix-s/2, iy+s/2, ix+s/2, iy-s/2);
    }
  }
}
class NodeCurve{
  float x1, y1, dt, t, dist;
  float[] xs, ys;
  PShape[] content = new PShape[0];;
  boolean active=true;//whether the filter currently approves of this curve.
  NodeCurve prev=null, next=null;
  boolean altt = true, altc = false; // any change that would require update_t() be called before next checking draw permission
  
  float[] x1t, y1t; // transformations of the locations to speed up filtering
  float[][] xst, yst;
  
  NodeCurve(float x1_, float y1_, float t_, float dt_, float[] xs_, float[] ys_){
    x1=x1_;y1=y1_;dt=dt_;xs=xs_;ys=ys_;t=t_;
    dist = sqrt(sq(x1 - xs[xs.length-1]) + sq(y1 - ys[xs.length-1]));
    x1t = new float[MAX_LENSES]; y1t = new float[MAX_LENSES];
    xst = new float[MAX_LENSES][xs.length]; yst = new float[MAX_LENSES][xs.length];
    update_t();
  }
  NodeCurve(Node a, Node b){
    x1 = a.x; y1=a.y; t=a.t; dt = a.dt;
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
    for(int k=0; k < FILTER.Lenses.size();k++){
      Lens LENS = FILTER.Lenses.get(k);
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
  
  public void make_content(){
    content = new PShape[4];
    for(int j=0;j<4;j++){
      PShape P = lower.base.createShape();
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
    if(COL_MODE==1){if(is_glance_edge()){return c1(alpha);}else{ return c2(alpha);}}
    if(COL_MODE==2){ return color((PI+atan2(y1 - ys[ys.length-1], x1 - xs[xs.length-1]))*(100/TWO_PI), 100, 80, alpha); }
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
    strokeWeight(2);
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
      stroke(get_color(80)); line( ((d-p)*xb + p*xa)/d, ((d-p)*yb + p*ya)/d, ((d-p-5)*xb + (p+5)*xa)/d, ((d-p-5)*yb + (p+5)*ya)/d);
      stroke(get_color(40)); line( ((d-p)*xb + p*xa)/d, ((d-p)*yb + p*ya)/d, ((d-p+5)*xb + (p-5)*xa)/d, ((d-p+5)*yb + (p-5)*ya)/d);
    }
  }
  public void time_mark(){ // draws the small saccade marks just under the timeline
    if(dist<BRIEF_MIN){stroke(c3(50));fill(c3(50));}else{stroke(get_color(50));fill(get_color(50));}
    if(next==null){return;}
    float x = (t-TIME.Tmin)*(width-200)/(TIME.Tmax - TIME.Tmin);
    float dx = (next.t - t)*(width-200)/(TIME.Tmax - TIME.Tmin);
    if(dx < 3){ line( x+100, height-40,  x+100, height-20); }
    else{ noStroke();rect( x+100, height-40, dx, 20); }
  }
}
// functionality for making and manipulating notes
class Notes{
  int note_selected = 0;
  boolean mode = false;
  ArrayList<String> notes = new ArrayList<String>();
  ArrayList<Integer> x = new ArrayList<Integer>(), y = new ArrayList<Integer>();
  public void draw(){
    fill(white(100)); stroke(white(100)); strokeWeight(2);
    for(int i=0;i<notes.size();i++){text(notes.get(i).replace("\\n","\n"), x.get(i), y.get(i));}
  }
  public void note_click(){
    for(int i=0; i<notes.size();i++){ if(notes.get(i).length()==0){notes.remove(i);x.remove(i);y.remove(i);i-=1;}} // clean up empty notes
    if(mouseButton == LEFT){ // add a new note
      mode=true;notes.add("");x.add(mouseX); y.add(mouseY); note_selected = notes.size()-1;
    }else if(mouseButton == RIGHT && notes.size()>0){
      // select closest note, and move to mouse pointer
      int closest = 0; float dist = sq(mouseX - x.get(0)) + sq(mouseY - y.get(0));
      for(int i=1;i<notes.size();i++){
        float d = sq(mouseX - x.get(i)) + sq(mouseY - y.get(i));
        if(d<dist){closest=i; dist=d;}
      }
      note_selected = closest;
      x.set(note_selected, mouseX); y.set(note_selected, mouseY);
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
  public void movie_time_clicked(){movie_update_time = Tmin + ((mouseX-100)/( width-200 )) * (Tmax - Tmin);}
  public String format_time(float t){return String.format("%01d", floor(t/3600)) +':'+ String.format("%02d", floor((t/60)%60)) +':'+String.format("%02d", floor(t%60));}
  public void zoom(){
    float nTmin = Tmin + Ts*(Tmax-Tmin), nTmax = Tmin + Te*(Tmax-Tmin);
    Tmin = nTmin; Tmax = nTmax; Ts=0; Te=1;
  }
  
  public void draw(){
    fill(white(100)); stroke(white(100)); strokeWeight(2);
    line(100,height-40,width-100,height-40);
    arc(100 + Ts*(width-200), height-40, 30, 30, HALF_PI, 3*HALF_PI);
    arc(100 + Te*(width-200), height-40, 30, 30, -HALF_PI, HALF_PI);
    float tts = Tmin + Ts*(Tmax-Tmin); float tte = Tmin + Te*(Tmax-Tmin);
    text(format_time(tts), 50 + Ts*(width-200), height-10);
    text(format_time(tte), 120 + Te*(width-200), height-10);
    // add marker for movie time
    if(clip!=null){
      float tc = clip.time();
      strokeWeight(1);stroke(white(100));
      float x = (tc-TIME.Tmin)/(TIME.Tmax - TIME.Tmin);
      line( x*(width-200)+100, height-20,  x*(width-200)+100, height-60);
    }
    // add user defined marks
    stroke(o1(70));strokeWeight(2);
    for(float i : markers){
      float c = (i-TIME.Tmin)/(TIME.Tmax - TIME.Tmin)*(width-200)+100;
      line(c, height-100, c, height-20);
    }
  }
  public void clicked(){
    if(mouseButton==LEFT){ // move the closest time bound
      float q = max(0, min(1, PApplet.parseFloat(mouseX - 100)/(width-200)));
      if( q < (Ts+Te)/2 ){Ts = q;}else{Te=q;}
    }else if(mouseButton==RIGHT){ // draw or delete a marker
      float clicked_time = Tmin + (Tmax-Tmin)*(mouseX-100)/(width-200);
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
// handles the bundling functionality
public void GPUsetup(){
  println("start GPUsetup");
  src_cl=join(loadStrings("forces.cl"),"\n");
  context = JavaCL.createBestContext();
  print("a"); queue = context.createDefaultQueue(); println("b"); // evil bug is trapped between these two prints
  byteOrder = context.getByteOrder(); program = context.createProgram(src_cl);
  inner = program.createKernel("inner"); outer = program.createKernel("outer");
  inner_directed = program.createKernel("inner_directed");
  GPU_setup = true;
}
public void make_pointers(int N){
  xPtr = allocateFloats(N).order(byteOrder); // pointers for the buffers
  yPtr = allocateFloats(N).order(byteOrder);
  lPtr = allocateFloats(N).order(byteOrder);
  fPtr = allocateFloats(2*16*N).order(byteOrder);
  current_buffer_size = N;
}

public void dynamic_bundling(float[] maxstep, int num_nodes, boolean[] included, boolean directed){
  //next, need to format them for the take_steps function
  if( current_buffer_size<num_nodes ){make_pointers(num_nodes);} // increase the pointer lengths if they aren't enough already
  // extract values from the curve arraylist, place in the buffers
  int up_to = 0;
  for(int i=0;i<node_curves.size();i++){
    NodeCurve c = node_curves.get(i);
    if( included[i] ){
      xPtr.set(up_to, c.x1); yPtr.set(up_to, c.y1); lPtr.set(up_to, 1.0f); up_to += 1;
      for(int j=0;j<c.xs.length-1;j++){ xPtr.set(up_to, c.xs[j]); yPtr.set(up_to, c.ys[j]); lPtr.set(up_to, 0.0f); up_to+=1; }
      xPtr.set(up_to, c.xs[c.xs.length-1]); yPtr.set(up_to, c.ys[c.xs.length-1]); lPtr.set(up_to, 1.0f); up_to += 1;
    }
  }
  //CLBuffer<Float> // prepare the buffers for the context
                  xb = context.createBuffer(Usage.InputOutput, xPtr); // the buffers
                  yb = context.createBuffer(Usage.InputOutput, yPtr);
                  lb = context.createBuffer(Usage.Input, lPtr);
                  fb = context.createBuffer(Usage.InputOutput, fPtr);
  if(directed){current=inner_directed;}else{current=inner;}
  current.setArgs(xb, yb, lb, fb, num_nodes, BIG_K);
  CLEvent addEvt;
  for(int iteration=0;iteration<maxstep.length;iteration++){ // iteratively bundle with values maxstep from the regime array
    outer.setArgs(xb, yb, lb, fb, num_nodes, maxstep[iteration]);
    addEvt = current.enqueueNDRange(queue, new int[] { num_nodes, 16 });
    fPtr = fb.read(queue, addEvt); // blocks until inner finished
    addEvt = outer.enqueueNDRange(queue, new int[] { num_nodes });
    xPtr = xb.read(queue, addEvt); yPtr = yb.read(queue, addEvt); // blocks until outer finished
  }
  //then return updated values back into the curve arraylist
  up_to = 0;
  for(int i=0;i<node_curves.size();i++){
    NodeCurve c = node_curves.get(i);
    if( included[i] ){
      up_to+=1;//skip over the first guy, he is locked anyway
      for(int j=0;j<c.xs.length-1;j++){ c.xs[j] = xPtr.get(up_to); c.ys[j] = yPtr.get(up_to); up_to+=1; }
      up_to+=1;//skip over the last guy, he is locked anyway
      c.altc= true;
    }
  }
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
// calls the bundle manager, needed for threading
public void autobundle(){
  float start_t = millis();
  is_bundling=true;
  bundle_manager(2);
  is_bundling=false;
  lower.has_changed = true;
  println("bundled in ", millis()-start_t);
}
// builds the node_curves from the nodes data, used on new CSV files or to clear the bundling
public int num_divs(float dist){return floor(max((dist/30.0f)-1, 2)); } // how many intermediate points to make, min of 2 to render properly
public void process(){
  node_curves = new ArrayList<NodeCurve>(nodes.size() - 1);
  for(int i=0;i<nodes.size()-1;i++){
    NodeCurve current = new NodeCurve( nodes.get(i), nodes.get(i+1) );
    if(node_curves.size()>0){;
      current.prev(node_curves.get(node_curves.size()-1));
      node_curves.get(node_curves.size()-1).next(current);
    }
    node_curves.add(current);
  }
  for(NodeCurve n : node_curves){n.make_content();} // Glance status is known at this point in time
}
// controls for user inputs (keys or mouse pressed, mousewheel, etc.)

public void keyPressed() {
  // note making controls
  if (notes.mode) {
    notes.note_key(); 
    return;
  }
  // ZOOMING
  else if (key == '+') { 
    if (mouseY<height-100) {
      z*= 1.2f;
    } else {
      TIME.zoom();
    }
  } else if (key == '-') { 
    if (mouseY<height-100) {
      z = 1;
    } else {
      TIME.reset();
    }
  } else if ( (key==',' || key==127) && FILTER.Lenses.size()>0) { // ',' or delete to remove selected lens
    FILTER.Lenses.remove(FILTER.selected);
    FILTER.selected = max(FILTER.selected-1, 0);
  } else if ( (key=='.' || key==10) && FILTER.Lenses.size()<MAX_LENSES) { // '.' or enter to add a new lens
    FILTER.Lenses.add(new Lens());
    FILTER.selected = FILTER.Lenses.size()-1; 
    FILTER.selected_time=millis();
    FILTER.update_all();
  }
  // special command for applying the dynamic bundling
  else if (key=='1') { 
    bundle_manager(1);
  } else if (key=='2') { 
    bundle_manager(2);
  } else if (key=='3') { 
    bundle_manager(3);
  }
  lower.has_changed = true;

  // backspace, deletes the closest node
  if ( key==BACKSPACE && nodes.size()>0 && !is_bundling) { // deletes the closest node, can't be done during bundling since it would corrupt the data
    int found = 0;
    float min_dist = sq(nodes.get(0).x - mouseX) + sq(nodes.get(0).y - mouseY);
    for (int i=1; i<nodes.size(); i++) {
      if (!TIME.within(nodes.get(i).t)) {
        continue;
      } // node is hidden by the time filter, so skip it
      float new_dist = sq(nodes.get(i).x - mouseX) + sq(nodes.get(i).y - mouseY);
      if (new_dist<min_dist) {
        found=i;
        min_dist=new_dist;
      }
    }
    Node f = nodes.get(found);
    if (f.LastNode != null) {
      f.LastNode.NextNode = f.NextNode;
    }
    if (f.NextNode != null) {
      f.NextNode.LastNode = f.LastNode;
    }
    nodes.remove(found);
    if (node_curves.size()>0) {
      if (found == node_curves.size()) {
        node_curves.remove(found-1);
      } // last guy, so just remove the edge going to it
      else {
        node_curves.remove(found);
      }
    }
    lower.has_changed=true;
  }

  //// quickstart : only really intended for development use, can remove later
  if (key=='q') { // DEV feature to load some files quickly, so I don't have to do it myself
    bgi = loadImage("q.png");
    fx = PApplet.parseFloat(lower.base.width)/(bgi.width); 
    fy = PApplet.parseFloat(lower.base.height)/(bgi.height);
    bgi.resize(lower.base.width, lower.base.height);
    attach("q.csv"); 
    process(); 
    TIME.reset();
  }
}

public void mouseWheel(MouseEvent event) {
  if (controller.isOpen &&mouseY < height-100 && mouseX > width-150) {
    controller.mouseWheel(event);
  } // controller gets to handle this
  else if (mouseY<height-100 && !notes.mode) {
    FILTER.adjust(event);
  }
  lower.has_changed = true;
}
public void mouseDragged() {
  mouseClicked();
}
public void mouseClicked() {
  if (controller.isOpen && mouseY<height-100 && mouseX > width-150) {
    controller.clicked();
  } // controller gets to handle this
  else if (mouseY > height-100) { // lower control panel gets to handle this
    if (mouseX > width-60) {
      controller.isOpen = !controller.isOpen; 
      return;
    } // swap the controls window
    else if (CLIP_MODE && (mouseX - 100)/( width - 200 )  > TIME.Ts && (mouseX - 100)/( width - 200 ) < TIME.Te ) {
      TIME.movie_time_clicked();
    } else {
      TIME.clicked();
    }
  } else if (mouseY < height-100 && notes.mode) {
    notes.note_click(); 
    return;
  } // notes gets to handle this
  else if (mouseY < height-100 && (!controller.isOpen || mouseX<width-150)) {
    FILTER.move();
  } // mouse is pressed on screen, so update the lenses
  lower.has_changed = true;
}
public void mousePressed() { 
  if (mouseButton==LEFT) {
    lmp=true;
  } else if (mouseButton==RIGHT) {
    rmp=true;
  }
}
public void mouseReleased() { 
  lmp = false; 
  rmp = false;
}
// functionality for loading from and saving to files

public void openStuff(){
  try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) { e.printStackTrace(); }
  fileChooser.updateUI();
  JDialog fileWrapper = new JDialog(); fileWrapper.setVisible(false); fileWrapper.setAlwaysOnTop(true);
  int result = fileChooser.showOpenDialog(fileWrapper);
  if(result != JFileChooser.APPROVE_OPTION){return;}
  String selectedFile = fileChooser.getSelectedFile().getAbsolutePath();
  println(selectedFile);
  if(selectedFile.toLowerCase().endsWith(".png")) { // makes the background image
    bgi = loadImage(selectedFile);
    fx = PApplet.parseFloat(lower.base.width)/bgi.width; fy = PApplet.parseFloat(lower.base.height)/bgi.height;
    bgi.resize(lower.base.width, lower.base.height);
  }
  else if(selectedFile.toLowerCase().endsWith(".bundle")){ loadBundle(selectedFile); TIME.reset(); }
  else if(selectedFile.toLowerCase().endsWith(".setting")){ loadSetting(selectedFile); check_setting(selectedFile); }
  else if(selectedFile.toLowerCase().endsWith(".mp4")){ clip = new Movie(this, selectedFile); }
  else if(selectedFile.toLowerCase().endsWith(".csv")){ attach(selectedFile); process(); TIME.reset();}
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
    lower.base.save(selectedFile+"_base.png");
    bgi.save(selectedFile+"_background.png");
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
  nodes = new ArrayList<Node>(M+1);
  if(M==0){return;} // no data present, and next line would throw an error
  TIME.TTmin = table.getRow(0).getFloat(0); TIME.TTmax = table.getRow(0).getFloat(0);
  for(int i=0;i<M;i++){
    TableRow r1 = table.getRow(i);
    Node current = new Node(r1.getFloat(0), r1.getFloat(1)*fx, r1.getFloat(2)*fy, r1.getFloat(3) );
    if(r1.getFloat(0)<TIME.TTmin){TIME.TTmin = r1.getFloat(0);}
    if(r1.getFloat(0)>TIME.TTmax){TIME.TTmax = r1.getFloat(0);}
    if(nodes.size()>0){
      current.last_node(nodes.get(nodes.size()-1));
      nodes.get(nodes.size()-1).next_node(current);
    }
    nodes.add(current);
  }
}

public void saveBundle(String selectedFile){
  PrintWriter writer;
  try{ writer = new PrintWriter(selectedFile, "UTF-8"); }
  catch (Exception e) { e.printStackTrace(); return; }
  writer.println("x,y,l,t,dt");
  // x, y, l, t, dt (last two zero except at locked nodes?)
  for(NodeCurve n : node_curves){
    printrow(writer, n.x1, n.y1, 1, n.t, n.dt);
    for(int i=0; i<n.xs.length;i++){
      printrow(writer, n.xs[i], n.ys[i], 0,0,0);
    }
  }
  Node n = nodes.get(nodes.size()-1); // final node to close the bundle
  printrow(writer, n.x, n.y, 1, n.t, n.dt);
  writer.close();
  println("Writer finished writing", writer, selectedFile);
}
public void printrow(PrintWriter w, float x, float y, float l, float t, float dt){
  for(float i : new float[] {x,y,l,t}){w.print(i);w.print(',');}
  w.print(dt);w.print('\n');
}
public void loadBundle(String bundlePath){
  table = loadTable(bundlePath, "header, csv");
  int M = table.getRowCount();
  if(M==0){return;} // don't bother with empty tables
  nodes = new ArrayList<Node>();
  node_curves = new ArrayList<NodeCurve>();
  TIME.TTmin = table.getRow(0).getFloat(3); TIME.TTmax = table.getRow(0).getFloat(3); // begin setting up time bounds
  for(int i=0;i<M;){
    TableRow r1 = table.getRow(i);
    float x = r1.getFloat(0), y=r1.getFloat(1), t = r1.getFloat(3), dt=r1.getFloat(4);
    int m = 0;
    while( i+m+1 < M && (m==0 || table.getRow(i+m+1).getFloat(2)==0) ){m++;}
    println(i, m);
    float[] xs=new float[m], ys = new float[m];
    for(int j=0; j<m; j++){
      xs[j] = table.getRow(i+j+1).getFloat(0); ys[j] = table.getRow(i+j+1).getFloat(1);
    }
    Node c1 = new Node( t,x,y,dt );
    if(r1.getFloat(3)<TIME.TTmin){TIME.TTmin = r1.getFloat(3);}
    if(r1.getFloat(3)>TIME.TTmax){TIME.TTmax = r1.getFloat(3);}
    if(m>0){
      NodeCurve c2 = new NodeCurve( x,y,t,dt,xs,ys );
      if(nodes.size()>0){
        c1.last_node(nodes.get(nodes.size()-1));
        nodes.get(nodes.size()-1).next_node(c1);
        node_curves.get(node_curves.size()-1).next(c2);
        c2.prev(node_curves.get(node_curves.size()-1));
      }
      node_curves.add(c2);
    }
    nodes.add(c1);
    i+=m+1;
  }
  TIME.Tmin = TIME.TTmin; TIME.Tmax = TIME.TTmax; TIME.Ts = 0; TIME.Te=1;
  for(NodeCurve n : node_curves){n.make_content();} // Glance status is known at this point in time
}



public void saveSetting(String selectedFile){
  PrintWriter writer;
  try{ writer = new PrintWriter(selectedFile, "UTF-8"); }catch(Exception e) { return; }
  // first row : global boolean parameters
  boolean[] basic_bools = new boolean[] {SHOW_SHORT,SHOW_GLANCE,SHOW_BASIC,GENERAL,SHOW_DOTS,HIST,BUNDLE,CLIP_MODE,USE_SIZE,ANIMATE,ALTERNATE,INTERLACE,TRAVEL_LINES};
  for(boolean b : basic_bools){writer.print(b);writer.print(',');}
  writer.println();
  // second row, global float parameters
  float[] basic_nums = new float[] {ALT_TIME_WINDOW, BRIEF_MIN, z, R, BIG_K, TIME.Ts, TIME.Te, TIME.Tmin, TIME.Tmax};
  for(float b : basic_nums){writer.print(b);writer.print(',');}
  writer.println();
  // third row, global int parameters
  int[] basic_ints = new int[] {FILTER.mode, COL_MODE};
  for(int b : basic_ints){writer.print(b);writer.print(',');}
  writer.println();
  // fourth row, time markers. first is length
  writer.print(TIME.markers.size());writer.print(',');for(float b :TIME.markers){writer.print(b);writer.print(',');}
  writer.println();
  // fifth row, number of lenses, other filter parameters
  writer.print(FILTER.Lenses.size());writer.print(',');
  int[] filter_props = new int[] {FILTER.Lenses.size(), FILTER.selected};
  for(int b : filter_props){writer.print(b);writer.print(',');}
  writer.println();
  // sixth row, the lenses themselves. blocks of 5 floats
  for(Lens ls : FILTER.Lenses){for(float i : new float[] {ls.WINDOW, ls.FACTOR, ls.LS, ls.mX, ls.mY}){writer.print(i);writer.print(',');}}
  writer.println();
  // seventh row, does the notes
  notes.write(writer); writer.println();
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
  HIST = r1.getString(5).equals("true");
  BUNDLE = r1.getString(6).equals("true");
  CLIP_MODE = r1.getString(7).equals("true");
  USE_SIZE = r1.getString(8).equals("true");
  ANIMATE = r1.getString(9).equals("true");
  ALTERNATE = r1.getString(10).equals("true");
  INTERLACE = r1.getString(11).equals("true");
  TRAVEL_LINES = r1.getString(12).equals("true");
  // second row, global numeric parameters
  r1 = set.getRow(1); println(r1.toString());
  ALT_TIME_WINDOW = r1.getFloat(0);
  BRIEF_MIN = r1.getFloat(1);
  z = r1.getFloat(2);
  R = r1.getFloat(3);
  BIG_K = r1.getFloat(4);
  TIME.Ts = r1.getFloat(5);
  TIME.Te = r1.getFloat(6);
  TIME.Tmin = r1.getFloat(7);
  TIME.Tmax = r1.getFloat(8);
  // third row, basic Int parameters
  r1 = set.getRow(2); println(r1.toString());
  FILTER.mode = r1.getInt(0); COL_MODE = r1.getInt(1);
  // fourth row, time markers length
  r1 = set.getRow(3);
  l = r1.getInt(0);
  TIME.markers = new ArrayList<Float>(); for(int i=0;i<l;i++){TIME.markers.add(r1.getFloat(i+1));}
  // fifth row, number of lenses, other filter parameters
  r1 = set.getRow(4); 
  l = r1.getInt(0); FILTER.selected = r1.getInt(1);
  // sixth row, the lenses themselves. blocks of 5 floats
  r1 = set.getRow(5);
  FILTER.Lenses = new ArrayList<Lens>();
  for(int i=0;i<l;i++){
    Lens newlen = new Lens();
    newlen.WINDOW = r1.getFloat(5*i+0); newlen.FACTOR = r1.getFloat(5*i+1); newlen.LS = r1.getFloat(5*i+2);
    newlen.update(r1.getFloat(5*i+3), r1.getFloat(5*i+4));
    FILTER.Lenses.add(newlen);
  }
  FILTER.selected = min(FILTER.selected, FILTER.Lenses.size()-1); // sanity check
  // do updates based on new filtering, knowledge of COL_MODE
  FILTER.update_all();
  for(NodeCurve n : node_curves){n.apply_color();}
  // seventh row, does the notes (can be after updating since doesn't affect anything)
  notes.read(set.getRow(6));
}

public void export_timeline(String selectedFile){
  PrintWriter writer;
  try{ writer = new PrintWriter(selectedFile, "UTF-8"); }catch(Exception e) { return; }
  writer.print("t,x,y,dt,");
  for(int i=0;i<FILTER.Lenses.size();i++){writer.print(i);writer.print(',');}
  writer.println();
  for(Node n : nodes){
    for(float a: new float[] {n.t, n.x, n.y, n.dt} ){writer.print(a);writer.print(',');}
    for(Lens l : FILTER.Lenses){writer.print(l.inside(n.x, n.y));writer.print(',');}
    writer.println();
  }
  writer.close();
  println("Writer finished writing", writer, selectedFile);
}
  public void settings() {  size(1700, 1000, P2D); }
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "Version19" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
