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

const versions = data.metadata.versioning.versions.version
    .filter(version => !version.includes("w")) // filter out weird snapshot versions like 0.25w14craftmine.3-beta
    .filter(version => !version.includes("beta"))
    .map(version => {
        const o = {};
        o.version = version;
        return o;
    });

const chunked = chunkify(versions, 6); // 256 is the max size of a gh actions matrix
let output = [];

chunked.forEach(chunk => {
    output.push(JSON.stringify(chunk));
})

process.stdout.write(JSON.stringify(output))

