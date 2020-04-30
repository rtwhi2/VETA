// VETA 0.27
import processing.video.*;
import static org.jocl.CL.*;
import static java.lang.System.*;
// file selection
import java.io.File; 
import javax.swing.*;
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
float R = 0.3; //reduced size inside the len
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
float BIG_K = 0.05; // 0.05 is I think an appropriate choice, until I next modify the other bundling parameters. But for more crowded screens, higher is needed.
boolean lmp = false, rmp = false; // (track whether left or right mouse is currently held down)
int dval = -1; int gval = -1; int aval = -1; // control the mouse-over-legend filtering functionality

//Colouring and other graphical features
color c1(float a){return color(17,100, 80,a);} // glances - background
color c2(float a){return color(50,100, 80,a);} // normal - background
color c3(float a){return color(70,100,100,a);} // close - background
color o1(float a){return color( 0, 60, 60,a);} // before - foreground
color o2(float a){return color(17, 60,100,a);} // glances - foreground
color o3(float a){return color(40,100, 80,a);} // after - foreground
color cy(float a, float type){return color((50 + (type*100*PHI))%100, 100, 80, a);} // colours based on data-defined type value
boolean wheel_test(float x){ return dval==-1 || (COL_MODE==2 && floor(4 + (8*x)/TWO_PI + 0.5)%8==dval); }
color color_wheel(float a, float x){ return color( ((floor(((PI+x)*8)/TWO_PI + 0.5)) * 12.5) % 100, 100, 80, a); }
color white(float a){return color(0, 0,100, a);} // plain white
color  grey(float a){return color(0, 0, 50, a);} // plain grey, used for out-of-filter time marks
color black(float a){return color(0, 0,  0, a);} // plain black
int[] sws = new int[] {2,4,8,12},  divs = new int[] {1,6,10,14}; // used in the node_curve alpha edge splattering
PFont  f; PFont  fb; // the font used for general text writing applications. defined in setup

void setup(){
  size(1700, 1000, P2D);
  surface.setTitle("Viz");
  src_cl=join(loadStrings("forces.cl"),"\n"); startmess= String.join("\n", loadStrings("startmess.txt")); blank = loadImage("blank.png");
  for(int i=0;i<4;i++){Frames[i] = new Frame(i);}
  controller = new ControlWindow();
  colorMode(HSB, 100);
  f = createFont("Arial",12,true); fb = createFont("Arial",22,true);
  println("ready", sketchFile(""), height, width);
}
void draw(){
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

void check_mouse(){
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
    int r = floor(4 + (8*x)/TWO_PI + 0.5)%8;
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
