import processing.video.*;
import com.nativelibs4java.opencl.*;
import com.nativelibs4java.opencl.CLMem.*;
import java.nio.ByteOrder;
import static java.lang.System.*;
import static org.bridj.Pointer.*;
// file selection
import java.io.File; 
import javax.swing.*;
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
float R = 0.3; //reduced size inside the len
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
float BIG_K = 0.05; // 0.05 is I think an appropriate choice, until I next modify the other bundling parameters. But for more crowded screens, higher is needed.
boolean lmp = false, rmp = false; // (track whether left or right mouse is currently held down)

//Colouring and other graphical features
color c1(float a){return color(17,100, 80,a);} // glances - background
color c2(float a){return color(50,100, 80,a);} // normal - background
color c3(float a){return color(70,100,100,a);} // close - background
color o1(float a){return color( 0, 60, 60,a);} // before - foreground
color o2(float a){return color(17, 60,100,a);} // glances - foreground
color o3(float a){return color(40,100, 80,a);} // after - foreground
color white(float a){return color(0, 0,100, a);} // plain white
color  grey(float a){return color(0, 0, 50, a);} // plain grey, used for out-of-filter time marks
color black(float a){return color(0, 0,  0, a);} // plain black
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

void setup(){
  size(1700, 1000, P2D);
  surface.setTitle("Viz");
  lower = new LowerLayer(); controller = new ControlWindow();
  colorMode(HSB, 100);
  f = createFont("Arial",12,true);
  println("ready", sketchFile(""), height, width);
  //GPU setup safety catch, needed for handling the demon bug on my device, probably isn't needed elsewhere
  thread("GPUsetup"); // tries its best to setup the openCL behaviour, but can freeze due to the demon bug, so threaded
}
void draw(){
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
