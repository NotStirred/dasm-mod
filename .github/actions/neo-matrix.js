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
    "21.10.63", // is broken on neogradle 7.1.11, future version should fix it.
]);

const versions = data.metadata.versioning.versions.version
    .filter(version => !version.includes("w")) // filter out weird snapshot versions like 0.25w14craftmine.3-beta
    .filter(version => !version.includes("beta"))
    .filter(version => !version_blacklist.has(version));

const chunked = chunkify(versions, 256); // 256 is the max size of a gh actions matrix
let output = [];

chunked.forEach(chunk => {
    output.push(JSON.stringify(chunk));
})

process.stdout.write(JSON.stringify(output));

