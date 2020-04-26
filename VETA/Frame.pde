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
  
  void inner_draw(){
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
  void draw(){
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
  
  
  void dynamic_bundling(float[] maxstep, int num_nodes, boolean[] included, boolean directed){
    println("mark1");
    float[] xb = new float[num_nodes], yb = new float[num_nodes], lb = new float[num_nodes], fb = new float[2*16*num_nodes];
    Pointer xPtr = Pointer.to(xb), yPtr = Pointer.to(yb), lPtr = Pointer.to(lb), fPtr = Pointer.to(fb);
    // extract values from the curve arraylist, place in the buffers
    int up_to = 0;
    for(int i=0;i<node_curves.size();i++){
      NodeCurve c = node_curves.get(i);
      if( included[i] ){
        xb[up_to] = c.x1; yb[up_to] = c.y1; lb[up_to] = 1.0; up_to += 1;
        for(int j=0;j<c.xs.length-1;j++){ xb[up_to] = c.xs[j]; yb[up_to] = c.ys[j]; lb[up_to] = 0.0; up_to+=1; }
        xb[up_to] = c.xs[c.xs.length-1]; yb[up_to] = c.ys[c.xs.length-1]; lb[up_to] = 1.0; up_to += 1;
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
  float[] s1 = {1,0.9,0.8,0.7,0.6,0.5,0.4,0.3,0.2,0.1,0.09,0.08,0.07,0.06,0.05,0.04,0.03,0.02,0.01,0.009,0.008,0.007,0.006,0.005,0.004,0.003,0.002,0.001};
  float[] s2 = {4,3.5,3.0, 2.6,2.3,2.0, 1.7,1.4,1.2, 1.0,0.6,0.4};
  float[] s3 = {100,80,64,51.2,41,32.8,26.2,21,16.8,13.4,10.7,8.56,6.85,5.48,4.38,3.5,2.45,1.71,1.2,0.84,0.588,0.411,0.2};
  void bundle_manager(int num){
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
  void process(){
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
  void delete(boolean interior){
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
  
  void timeHist(ArrayList<Integer> inst, float tval, boolean isgen){
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
    
  void lensHist(ArrayList<Integer> inst, float tval, boolean isgen){
    if(FILTER.Lenses.size()==0){return;}
    Lens l = FILTER.Lenses.get(FILTER.selected);
    int BINS = 120; float[] vals = new float[BINS];
    for(int i=0;i<BINS;i++){
      float angle = TWO_PI*((i+0.5)/BINS);
      // it would be cool to have an explicit formula here, and I spent a while trying to find one, but alas I could not.
      vals[i] = l.WINDOW/2; float step = 2; // instead lets do a search in each direction for the lens border
      for(int k=1;k<30;k++){
        if( l.inside(l.mX + cos(angle)*vals[i], l.mY + sin(angle)*vals[i]) ){ vals[i]*=step; }else{ vals[i]/=step; } step= 1 + 0.7*(step-1);
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
        if(isgen){n.t_animate_mark_2(n.inst, 0, tval, l.mX+ cos(TWO_PI*(bin+0.5)/BINS)*vals[bin], l.mY+ sin(TWO_PI*(bin+0.5)/BINS)*vals[bin]);}
        else{     n.t_animate_mark_2(n.inst, 1, tval, l.mX+ cos(TWO_PI*(bin+0.5)/BINS)*vals[bin], l.mY+ sin(TWO_PI*(bin+0.5)/BINS)*vals[bin]);}
        vals[bin] += SIZE;
      }
    }
  }
}

ArrayList<Node> NodeSort(ArrayList<Node> n, float x, float y){ // sub method for distance based sorting
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

int num_divs(float dist){return floor(max((dist/30.0)-1, 2)); } // how many intermediate points to make, min of 2 to render properly
