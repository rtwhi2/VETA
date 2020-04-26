// handles the bundling functionality
void GPUsetup(){
  println("start GPUsetup");
  src_cl=join(loadStrings("forces.cl"),"\n");
  context = JavaCL.createBestContext();
  print("a"); queue = context.createDefaultQueue(); println("b"); // evil bug is trapped between these two prints
  byteOrder = context.getByteOrder(); program = context.createProgram(src_cl);
  inner = program.createKernel("inner"); outer = program.createKernel("outer");
  inner_directed = program.createKernel("inner_directed");
  GPU_setup = true;
}
void make_pointers(int N){
  xPtr = allocateFloats(N).order(byteOrder); // pointers for the buffers
  yPtr = allocateFloats(N).order(byteOrder);
  lPtr = allocateFloats(N).order(byteOrder);
  fPtr = allocateFloats(2*16*N).order(byteOrder);
  current_buffer_size = N;
}

void dynamic_bundling(float[] maxstep, int num_nodes, boolean[] included, boolean directed){
  //next, need to format them for the take_steps function
  if( current_buffer_size<num_nodes ){make_pointers(num_nodes);} // increase the pointer lengths if they aren't enough already
  // extract values from the curve arraylist, place in the buffers
  int up_to = 0;
  for(int i=0;i<node_curves.size();i++){
    NodeCurve c = node_curves.get(i);
    if( included[i] ){
      xPtr.set(up_to, c.x1); yPtr.set(up_to, c.y1); lPtr.set(up_to, 1.0); up_to += 1;
      for(int j=0;j<c.xs.length-1;j++){ xPtr.set(up_to, c.xs[j]); yPtr.set(up_to, c.ys[j]); lPtr.set(up_to, 0.0); up_to+=1; }
      xPtr.set(up_to, c.xs[c.xs.length-1]); yPtr.set(up_to, c.ys[c.xs.length-1]); lPtr.set(up_to, 1.0); up_to += 1;
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
// calls the bundle manager, needed for threading
void autobundle(){
  float start_t = millis();
  is_bundling=true;
  bundle_manager(2);
  is_bundling=false;
  lower.has_changed = true;
  println("bundled in ", millis()-start_t);
}
// builds the node_curves from the nodes data, used on new CSV files or to clear the bundling
int num_divs(float dist){return floor(max((dist/30.0)-1, 2)); } // how many intermediate points to make, min of 2 to render properly
void process(){
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
