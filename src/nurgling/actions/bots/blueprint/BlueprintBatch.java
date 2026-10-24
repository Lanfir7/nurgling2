package nurgling.actions.bots.blueprint;

import nurgling.i18n.L10n;

import nurgling.planning.PlanningGhost;
import java.util.List;

/** The same fail-stop sequence used by the live builder and by scripted receipt tests. */
final class BlueprintBatch {
    interface Port {
        boolean unchanged(PlanningGhost ghost);
        void build(GhostJob job) throws InterruptedException, GhostConstruction.FatalException;
        boolean removeIfUnchanged(PlanningGhost ghost);
        void progress(GhostJob job, int index, int total);
        void finished(GhostJob job, int index, int total);
    }

    static final class Outcome {
        final String failure;
        final int attempted;
        Outcome(String failure, int attempted) { this.failure = failure; this.attempted = attempted; }
    }

    static Outcome run(List<GhostJob> jobs, boolean removeBuilt, boolean stopOnKnownSkip, Port port)
            throws InterruptedException {
        int attempted = 0;
        for (GhostJob job : jobs) {
            if (!port.unchanged(job.ghost))
                return new Outcome(L10n.get("bbuilder.safe.changed"), attempted);
            port.progress(job, ++attempted, jobs.size());
            try {
                port.build(job);
            } catch (GhostConstruction.FatalException failure) {
                return new Outcome(failure.getMessage(), attempted);
            }
            if (!port.unchanged(job.ghost))
                return new Outcome(L10n.get("bbuilder.safe.changed"), attempted);
            if (job.status == GhostJob.Status.BUILT || job.status == GhostJob.Status.ALREADY_BUILT) {
                if (removeBuilt && !port.removeIfUnchanged(job.ghost))
                    return new Outcome(L10n.get("bbuilder.safe.changed"), attempted);
            } else if (job.status == GhostJob.Status.PENDING) {
                return new Outcome(L10n.get("bbuilder.safe.unconfirmed"), attempted);
            } else if (stopOnKnownSkip) {
                port.finished(job, attempted, jobs.size());
                return new Outcome(job.reason, attempted);
            }
            port.finished(job, attempted, jobs.size());
        }
        return new Outcome(null, attempted);
    }
}
