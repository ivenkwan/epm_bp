const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
function normalizeMavenBuildMeta(bom) {
  for (const component of bom.components) {
    const references = (component.externalReferences || []).filter((reference) => reference.type !== "build-meta");
    if (references.length > 0) {
      component.externalReferences = references;
    } else {
      delete component.externalReferences;
    }
  }
}

const root = process.argv[2];
const commit = process.argv[3];
const requiredDirectPackages = ["react@19.3.1", "vite@7.3.7", "typescript@6.0.3", "@playwright/test@1.64.1"];

function readSbom(relativePath) {
  const absolutePath = path.join(root, relativePath);
  if (!fs.existsSync(absolutePath)) {
    throw new Error(`required SBOM is missing: ${relativePath}`);
  }
  const bom = JSON.parse(fs.readFileSync(absolutePath, "utf8"));
  if (bom.bomFormat !== "CycloneDX" || !Array.isArray(bom.components) || bom.components.length === 0) {
    throw new Error(`invalid or empty SBOM: ${relativePath}`);
  }
  if (bom.metadata) {
    delete bom.metadata.timestamp;
    if (relativePath === "target/workspace-bom.json" && bom.metadata.component) {
      bom.metadata.component.name = "finance-platform";
    }
  }
  for (const component of bom.components) {
    if (component.type === "file" && typeof component.name === "string") {
      component.name = component.name.replace(/\\/g, "/").split("/").pop();
    }
  }
  normalizeMavenBuildMeta(bom);
  delete bom.serialNumber;
  const digest = crypto.createHash("sha256").update(JSON.stringify(bom)).digest("hex");
  const deterministicUuid = [
    digest.slice(0, 8),
    digest.slice(8, 12),
    `8${digest.slice(13, 16)}`,
    `a${digest.slice(17, 20)}`,
    digest.slice(20, 32),
  ].join("-");
  bom.serialNumber = `urn:uuid:${deterministicUuid}`;
  fs.writeFileSync(absolutePath, `${JSON.stringify(bom, null, 2)}\n`);
  return bom;
}

const mavenBom = readSbom("target/bom.json");
const workspaceBom = readSbom("target/workspace-bom.json");
const componentNames = new Set(workspaceBom.components.map((component) => `${component.name}@${component.version}`));
for (const required of requiredDirectPackages) {
  if (!componentNames.has(required)) {
    throw new Error(`workspace SBOM is missing locked package ${required}`);
  }
}
if (!Array.isArray(mavenBom.components) || mavenBom.components.length === 0) {
  throw new Error("Maven SBOM contains no components");
}

const inputFiles = [
  ".github/workflows/ci.yml",
  ".gitleaksignore",
  ".mvn/wrapper/maven-wrapper.properties",
  "apps/finance-web/package.json",
  "docs/decisions/version-matrix.md",
  "package.json",
  "pnpm-lock.yaml",
  "pnpm-workspace.yaml",
  "pom.xml",
  "tests/e2e/package.json",
  "tests/integration/toolchain/ToolchainCompatibilityTest.java",
];
const inputs = {};
for (const relativePath of inputFiles.sort()) {
  inputs[relativePath] = {
    sha256: crypto.createHash("sha256").update(fs.readFileSync(path.join(root, relativePath))).digest("hex"),
  };
}
const sboms = {};
for (const relativePath of ["target/bom.json", "target/workspace-bom.json"]) {
  sboms[relativePath] = {
    sha256: crypto.createHash("sha256").update(fs.readFileSync(path.join(root, relativePath))).digest("hex"),
  };
}
const provenance = {
  schemaVersion: 1,
  commit,
  inputs,
  sboms,
};
const outputPath = path.join(root, "target", "provenance.json");
fs.writeFileSync(outputPath, `${JSON.stringify(provenance, null, 2)}\n`);
console.log(`PASS: provenance verified for ${commit.slice(0, 7)}; target/provenance.json generated`);
