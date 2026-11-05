const result = document.getElementById("auth-complete");
if (result && window.parent !== window) {
  window.parent.postMessage({ type: "bytedepth-auth", outcome: result.dataset.outcome }, window.location.origin);
}
