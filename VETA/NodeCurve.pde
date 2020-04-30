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
  void update_t(){ // updates the transformed location variables
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
  void prev(NodeCurve prev){this.prev = prev; altc=true;}
  void next(NodeCurve next){this.next = next; altc=true;}
  boolean do_wheel_test(){return wheel_test(atan2(ys[ys.length-1]-y1, xs[xs.length-1] - x1));}
  
  void make_content(){
    content = new PShape[4];
    for(int j=0;j<4;j++){
      PShape P = f.lower.base.createShape();
      P.beginShape();P.noFill();P.stroke( get_color(25.0/divs[j]) );
      P.strokeWeight(sws[j]);
      P.vertex(x1,y1);
      for(int i=0;i<xs.length;i++){P.vertex(xs[i],ys[i]); }
      P.endShape();
      content[j] = P;
    }
    apply_color();
  }
  void apply_color(){
    if(content.length==4){
      for(int j=0;j<4;j++){
        content[j].setStroke( get_color(25.0/divs[j]) );
      }
    }
  }
  color get_color(float alpha){
    if( dist < BRIEF_MIN && COL_MODE < 2){return c3(alpha); }
    if(COL_MODE==1){if(is_glance_edge()){return c1(alpha);}else{ return c2(alpha);}}
    if(COL_MODE==2){ return color_wheel(alpha, atan2(y1 - ys[ys.length-1], x1 - xs[xs.length-1]));}
    if(COL_MODE==3){ return cy(alpha, type); }
    return c2(alpha); // just use one colour for all saccades
  }
  void update_content(){
    altc = false;
    for(int j=0;j<4;j++){
      for(int i=0; i<xs.length-1;i++){ content[j].setVertex(i+1, xs[i], ys[i]);}
    }
    apply_color();
  }
  boolean is_glance_node(){
    if(prev==null || next==null){return false;}
    float dl = sq(x1 - prev.x1) + sq(y1 - prev.y1);
    float dn = sq(x1 - next.x1) + sq(y1 - next.y1);
    float db = sq(next.x1 - prev.x1) + sq(next.y1 - prev.y1);
    return db*sq(GLANCE_STANDARD) < min(dl, dn);
  }
  boolean is_glance_edge(){ return is_glance_node() || (next!=null && next.is_glance_node()); }
  void firefly(){ // unexpectedly long and complicated code for doing the animated fireflies
    if( !active ||  dist<BRIEF_MIN ){ return; }
    float SPEED = 0.02; // controls how fast the fireflies move, in pixels per millisecond
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
  void time_mark(){ // draws the small saccade marks just under the timeline
    stroke(get_color(50));fill(get_color(50));
    if(next==null){return;}
    float x = (t-f.TIME.Tmin)*TWIDTH/(f.TIME.Tmax - f.TIME.Tmin);
    float dx = (next.t - t)*TWIDTH/(f.TIME.Tmax - f.TIME.Tmin);
    if(dx < 3){ line( x+TBUFFER, height-40,  x+TBUFFER, height-20); }
    else{ noStroke();rect( x+TBUFFER, height-40, dx, 20); }
  }
}
