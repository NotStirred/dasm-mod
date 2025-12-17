#!/usr/bin/env node
import { XMLParser } from "fast-xml-parser";

function chunkify(array, n) {
    let result = [];
    while(array.length > 0) {
        const sliced = array.splice(0, Math.min(n, array.length));
        result.push(sliced);
    }
    return result;
}

const response = await fetch("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml");
if (!response.ok) {
    // noinspection ExceptionCaughtLocallyJS
    throw new Error(`Neoforge versions response status: ${response.status}`);
}

const parser = new XMLParser();
let data = parser.parse(await response.text());

const version_blacklist = new Set([
    "20.6.120", // neoFormRecompile fails
    "20.6.121", // neoFormRecompile fails
    "20.6.122", // neoFormRecompile fails
    "20.6.123", // neoFormRecompile fails
    "21.10.63", // is broken on neogradle 7.1.11, future version should fix it.
]);

let versions = data.metadata.versioning.versions.version
    .filter(version => !version.includes("w")) // filter out weird snapshot versions like 0.25w14craftmine.3-beta
    .filter(version => !version_blacklist.has(version));

const onlyHighestPatch = process.env.ONLY_HIGHEST_PATCH || "false" === "true";
if (onlyHighestPatch) {
    const versionRegex = /^(?<major>[0-9]+)\.(?<minor>[0-9]+)\.(?<patch>[0-9]+)(?<extra>.*)$/;
    const highestPatchForMajorMinorPair = new Map(); // key is eg: 21.10, value is 64 (for neoforge version 21.10.64)
    versions.forEach(version => {
        const groups = version.match(versionRegex).groups;

        const majorMinorPair = groups.major.toString() + '.' + groups.minor.toString();

        const existing = highestPatchForMajorMinorPair.get(majorMinorPair);
        const newPatch = parseInt(groups.patch);

        if (existing === undefined || existing === null || newPatch > existing.patch) {
            const val = { "patch": newPatch, "extra": groups.extra };
            highestPatchForMajorMinorPair.set(majorMinorPair, val);
        }
    });

    versions = [];
    highestPatchForMajorMinorPair.forEach((value, key, _) => {
        versions.push(key + '.' + value.patch + value.extra);
    });
}

const chunked = chunkify(versions, 256); // 256 is the max size of a gh actions matrix
let output = [];

chunked.forEach(chunk => {
    output.push(JSON.stringify(chunk));
})

process.stdout.write(JSON.stringify(output));

