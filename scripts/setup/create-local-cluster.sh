#!/usr/bin/env bash
# Create (or reuse) the local EPM finance platform cluster with kind.
# Idempotent: an existing healthy cluster with the same name is reused.
# Host port mappings exposed for local smoke testing:
#   18080 -> APISIX http gateway   18081 -> Keycloak http   18888 -> OTel collector metrics
set -euo pipefail

CLUSTER_NAME="${EPM_KIND_CLUSTER:-epm-finance}"
KUBE_CONTEXT="kind-${CLUSTER_NAME}"

command -v kind >/dev/null 2>&1 || { echo "FAIL: kind is not installed" >&2; exit 1; }
command -v kubectl >/dev/null 2>&1 || { echo "FAIL: kubectl is not installed" >&2; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "FAIL: docker is not installed" >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "FAIL: docker daemon is unreachable" >&2; exit 1; }

if kubectl config get-contexts -o name 2>/dev/null | grep -qx "${KUBE_CONTEXT}"; then
  if kubectl --context "${KUBE_CONTEXT}" get --raw /readyz >/dev/null 2>&1; then
    echo "[cluster] reusing healthy existing cluster ${CLUSTER_NAME}"
    kubectl config use-context "${KUBE_CONTEXT}" >/dev/null
    exit 0
  fi
  echo "[cluster] context exists but not ready; deleting and recreating"
  kind delete cluster --name "${CLUSTER_NAME}"
fi

CONFIG="$(mktemp /tmp/epm-kind.XXXXXX.yaml)"
trap 'rm -f "${CONFIG}"' EXIT
cat > "${CONFIG}" <<EOF
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: ${CLUSTER_NAME}
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30080
        hostPort: 18080
        protocol: TCP
      - containerPort: 30081
        hostPort: 18081
        protocol: TCP
      - containerPort: 30088
        hostPort: 18888
        protocol: TCP
EOF

echo "[cluster] creating kind cluster ${CLUSTER_NAME}"
kind create cluster --config "${CONFIG}" --wait 120s
kubectl config use-context "${KUBE_CONTEXT}" >/dev/null
kubectl wait --for=condition=Ready node --all --timeout=180s
echo "PASS: cluster ${CLUSTER_NAME} ready (gateway :18080, keycloak :18081, collector metrics :18888)"
