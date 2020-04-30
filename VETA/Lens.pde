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
  void update(float x_, float y_){ mX=x_; mY=y_; mXt = (2*x_/exp(-FACTOR))/WINDOW; mYt = (2*y_/exp(FACTOR))/WINDOW;}
  
  void draw(boolean selected){
    f.base.noFill();
    f.base.stroke(white(100));
    f.base.strokeWeight(1);
    if(selected){f.base.strokeWeight(3);}
    float fX = exp(-FACTOR)*WINDOW/2, fY = exp(FACTOR)*WINDOW/2;
    if(LS==32.0){ f.base.rect(mX-fX, mY-fY, 2*fX, 2*fY);} // much easier to draw a rectangle
    else{
      f.base.beginShape(); // inb4 this is bad coding practice
      for(float i=-0.5;i<0.5;i=i+0.02){f.base.vertex(mX+fX*(i/abs(i))*pow(  abs(i), 1/LS),mY+fY*           pow(1-abs(i), 1/LS));}
      for(float i=-0.5;i<0.5;i=i+0.02){f.base.vertex(mX+fX*           pow(1-abs(i), 1/LS),mY-fY*(i/abs(i))*pow(  abs(i), 1/LS));}
      for(float i=-0.5;i<0.5;i=i+0.02){f.base.vertex(mX-fX*(i/abs(i))*pow(  abs(i), 1/LS),mY-fY*           pow(1-abs(i), 1/LS));}
      for(float i=-0.5;i<0.5;i=i+0.02){f.base.vertex(mX-fX*           pow(1-abs(i), 1/LS),mY+fY*(i/abs(i))*pow(  abs(i), 1/LS));}
      f.base.endShape(CLOSE);
    }
    float t_inside=0, t_total = 0;
    for(Node n: f.nodes){ if(f.TIME.within(n.t)){t_total+=n.dt; if(inside(n.x,n.y)){t_inside+=n.dt;}} }
    f.base.fill(white(100)); f.base.textFont(fb);
    //f.base.text( String.format("%.02f", (100*t_inside/t_total))+"%", mX-15, mY + WINDOW*exp(FACTOR)/2 - 10);
  }
  // whether an untransformed point is inside the lens shape
  boolean inside(float x, float y){
    return inside_t( 2*x/exp(-FACTOR)/WINDOW, 2*y/exp(FACTOR)/WINDOW );
  }
  boolean inside_t(float x, float y){ // whether a point is inside the lens shape, that has been transformed
    float dx = abs(x-mXt); float dy = abs(y-mYt);
    if(LS==32.0){ return dx<1 && dy<1; } // the lens is a true square, so use infinity norm
    if( dx>1 || dy>1 ){ return false; } // not even in the square, so no chance
    return pow(dx, LS)+pow(dy, LS) < 1; // use the LS P-norm
  }
  // whether a line crosses the lens boundary
  boolean safe_t( float x1, float y1, float x2, float y2){
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
  boolean accept_t(float x1, float y1, float[] xs, float[] ys){
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
  
  void adjust(MouseEvent event) {
    if( (mousePressed && rmp) ||  (keyPressed && key=='m') ){ // right mouse key adjustment
      LS = LS*exp(-log(2)*event.getCount()/3); // modify shape
      LS = min(32, max(1, LS));
    }else if(  (mousePressed && lmp) ||  (keyPressed && key=='n') ){ // left mouse key adjustment
      WINDOW = WINDOW - (5 + WINDOW/15)*event.getCount(); // modify size
      WINDOW = min(2000*exp(abs(FACTOR)), max(15, WINDOW));
    }else{ // no mouse key adjustment
      FACTOR = FACTOR - 0.05*event.getCount(); // modify squish
      FACTOR = min(5, max(-5, FACTOR));
    }
    update(mX, mY);
  }
}
