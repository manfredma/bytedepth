package manfred.bytedepth.infrastructure.stats;

public final class ViewLogTablespaceMetrics {

    private long dataLength;
    private long dataFreeBytes;

    public ViewLogTablespaceMetrics() {
    }

    public ViewLogTablespaceMetrics(long dataLength, long dataFreeBytes) {
        this.dataLength = dataLength;
        this.dataFreeBytes = dataFreeBytes;
    }

    public long getDataLength() {
        return dataLength;
    }

    public void setDataLength(long dataLength) {
        this.dataLength = dataLength;
    }

    public long getDataFreeBytes() {
        return dataFreeBytes;
    }

    public void setDataFreeBytes(long dataFreeBytes) {
        this.dataFreeBytes = dataFreeBytes;
    }
}
