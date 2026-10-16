package manfred.bytedepth.app.annotation;

import manfred.bytedepth.domain.annotation.PlacementReason;

public final class InvalidAnchorException extends IllegalArgumentException {
    private final PlacementReason reason;

    public InvalidAnchorException(PlacementReason reason) {
        super("Invalid annotation selector: " + reason);
        this.reason = reason;
    }

    public PlacementReason reason() {
        return reason;
    }
}
