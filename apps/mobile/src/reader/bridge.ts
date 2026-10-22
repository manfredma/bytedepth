import type { AnnotationEnvelope, ContentIdentity, ReaderBridgeMessage } from "../contracts/mobile";
import { parseBridgeMessage } from "../contracts/mobile";
export function validateBridgeMessage(
  raw: string,
  expected: ContentIdentity & { generation: number },
): ReaderBridgeMessage | null {
  try {
    const message = parseBridgeMessage(raw, expected);
    if (message.type === "position" && message.payload.projectionVersion !== expected.projectionVersion) return null;
    return message;
  } catch {
    return null;
  }
}
export function matchingAnnotations(
  envelope: AnnotationEnvelope | null | undefined,
  identity: ContentIdentity,
): AnnotationEnvelope | null {
  return envelope?.schemaVersion === 1 &&
    envelope.postId === identity.postId &&
    envelope.contentVersion === identity.contentVersion &&
    envelope.projectionVersion === identity.projectionVersion &&
    envelope.digest === identity.digest
    ? envelope
    : null;
}
