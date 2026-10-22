import type { ContentIdentity } from "../contracts/mobile";
export function sameRepresentation(a: ContentIdentity, b: ContentIdentity): boolean {
  return (
    a.postId === b.postId &&
    a.contentVersion === b.contentVersion &&
    a.projectionVersion === b.projectionVersion &&
    a.digest === b.digest &&
    a.representationHash === b.representationHash &&
    a.shellVersion === b.shellVersion
  );
}
