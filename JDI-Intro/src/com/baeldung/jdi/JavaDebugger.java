package com.baeldung.jdi;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Map;
import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.StepRequest;
public class JavaDebugger {
//what if you have multiples classes
    private Class debugClass;
    private VirtualMachine vm;
    private int[] breakPointLines;
    public Class getDebugClass() {
        return debugClass;
    }
    /*public JavaDebugger(VirtualMachine vm){
        this.vm=vm;
        //find the main thread

    }*/
    public void setDebugClass(Class debugClass) {
        this.debugClass = debugClass;
    }
    public int[] getBreakPointLines() {
        return breakPointLines;
    }
    public void setBreakPointLines(int[] breakPointLines) {
        this.breakPointLines = breakPointLines;
    }
    /**
     * Sets the debug class as the main argument in the connector and launches the VM
     * @return VirtualMachine
     * @throws IOException
     * @throws IllegalConnectorArgumentsException
     * @throws VMStartException
     */
    public VirtualMachine connectAndLaunchVM() throws IOException, IllegalConnectorArgumentsException, VMStartException {
        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        arguments.get("main").setValue(debugClass.getName());
        VirtualMachine vm = launchingConnector.launch(arguments);
        return vm;
    }
    /**
     * Creates a request to prepare the debug class, add filter as the debug class and enables it
     * @param vm
     */
    public void enableClassPrepareRequest(VirtualMachine vm) {
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(debugClass.getName());
        classPrepareRequest.enable();
    }
    /**
     * Sets the break points at the line numbers mentioned in breakPointLines array
     * @param vm
     * @param event
     * @throws AbsentInformationException
     */
    public void setBreakPoints(VirtualMachine vm, ClassPrepareEvent event) throws AbsentInformationException {
        ClassType classType = (ClassType) event.referenceType();
        for(int lineNumber: breakPointLines) {
            Location location = classType.locationsOfLine(lineNumber).get(0);
            BreakpointRequest bpReq = vm.eventRequestManager().createBreakpointRequest(location);
            bpReq.enable();
        }
    }
    /**
     * Displays the visible variables
     * @param event
     * @throws IncompatibleThreadStateException
     * @throws AbsentInformationException
     */
    public void displayVariables(LocatableEvent event) throws IncompatibleThreadStateException, AbsentInformationException {
        StackFrame stackFrame = event.thread().frame(0);
        if(stackFrame.location().toString().contains(debugClass.getName())) {
            Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(stackFrame.visibleVariables());
            System.out.println("Variables at " +stackFrame.location().toString() +  " > ");
            for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
                System.out.println(entry.getKey().name() + " = " + entry.getValue());
            }
        }
    }
    /**
     * Enables step request for a break point
     * @param vm
     * @param event
     */
    public void enableStepRequest(VirtualMachine vm, BreakpointEvent event) {
        //enable step request for last break point
        if(event.location().toString().contains(debugClass.getName()+":"+breakPointLines[breakPointLines.length-1])) {
            StepRequest stepRequest = vm.eventRequestManager().createStepRequest(event.thread(), StepRequest.STEP_LINE, StepRequest.STEP_OVER);
            stepRequest.enable();
        }
    }
    public void enableStepRequest(VirtualMachine vm) {
              ThreadReference mainThread=null;
      for (ThreadReference r : vm.allThreads()) {
                //find the thread with the "main" and store as a local variable
                //Save it as local variable in the class also the VM
                //System.out.println("\t"+r.name() + " " + r.status() + " " + r.frameCount());
                if(r.name().equals(
                        "main"))
                {
                    mainThread=r;
                    break;
                }
            }
        if(mainThread!=null) {
            System.out.println("Setting the step request");
            StepRequest stepRequest = vm.eventRequestManager().createStepRequest(mainThread, StepRequest.STEP_LINE, StepRequest.STEP_OVER);
            stepRequest.enable();
        } else {
            System.out.println("main thread is not found");
        }
    }
    public static void main(String[] args) throws Exception {
        JavaDebugger debuggerInstance = new JavaDebugger();
        debuggerInstance.setDebugClass(JDIExampleDebuggee.class);
        int[] breakPoints = {6, 9};
        //debuggerInstance.setBreakPointLines(breakPoints);
        VirtualMachine vm = null;
        try {
            vm = debuggerInstance.connectAndLaunchVM();

            debuggerInstance.enableClassPrepareRequest(vm);

            EventSet eventSet = null;
            while ((eventSet = vm.eventQueue().remove()) != null) {
                for (Event event : eventSet) {
                    System.out.println("\t\t"+event);
                    if (event instanceof ClassPrepareEvent) {
                        debuggerInstance.enableStepRequest(vm);

                     //   debuggerInstance.setBreakPoints(vm, (ClassPrepareEvent)event);
                    }
                   /* if (event instanceof BreakpointEvent) {
                        event.request().disable();
                        debuggerInstance.displayVariables((BreakpointEvent) event);
                        debuggerInstance.enableStepRequest(vm, (BreakpointEvent)event);
                        debuggerInstance.enableStepRequest(vm);
                    }*/
                    if (event instanceof StepEvent) {
                        debuggerInstance.displayVariables((StepEvent) event);
                        debuggerInstance.enableStepRequest(vm);
                    }
                    vm.resume();
                }
            }
        } catch (VMDisconnectedException e) {
            System.out.println("Virtual Machine is disconnected.");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            InputStreamReader reader = new InputStreamReader(vm.process().getInputStream());
            OutputStreamWriter writer = new OutputStreamWriter(System.out);
            char[] buf = new char[512];
            reader.read(buf);
            writer.write(buf);
            writer.flush();
        }
    }
}