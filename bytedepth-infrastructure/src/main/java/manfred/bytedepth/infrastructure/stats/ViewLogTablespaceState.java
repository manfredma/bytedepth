package manfred.bytedepth.infrastructure.stats;

public final class ViewLogTablespaceState {

    private long deletedRowsSinceOptimize;

    public ViewLogTablespaceState() {
    }

    public ViewLogTablespaceState(long deletedRowsSinceOptimize) {
        this.deletedRowsSinceOptimize = deletedRowsSinceOptimize;
    }

    public long getDeletedRowsSinceOptimize() {
        return deletedRowsSinceOptimize;
    }

    public void setDeletedRowsSinceOptimize(long deletedRowsSinceOptimize) {
        this.deletedRowsSinceOptimize = deletedRowsSinceOptimize;
    }
}
