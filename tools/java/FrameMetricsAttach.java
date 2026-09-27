package codex.frameexport;

import com.sun.tools.attach.VirtualMachine;

/** Runs outside the client with the same JDK; the agent does all target-side work. */
public final class FrameMetricsAttach {
    private FrameMetricsAttach() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4)
            throw new IllegalArgumentException("usage: PID AGENT_JAR ABSOLUTE_CSV ABSOLUTE_STATUS");
        long pid = Long.parseLong(args[0]);
        if (pid <= 0 || args[2].indexOf('|') >= 0 || args[3].indexOf('|') >= 0)
            throw new IllegalArgumentException("invalid PID or output path");
        VirtualMachine target = VirtualMachine.attach(Long.toString(pid));
        try {
            target.loadAgent(args[1], args[2] + "|" + args[3]);
        } finally {
            target.detach();
        }
    }
}
