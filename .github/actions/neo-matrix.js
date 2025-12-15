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

const text = `<metadata>
<groupId>net.neoforged</groupId>
<artifactId>neoforge</artifactId>
<versioning>
<latest>21.11.6-beta</latest>
<release>21.11.6-beta</release>
<versions>
<version>20.2.12-beta</version>
<version>20.2.93</version>
<version>20.3.8-beta</version>
<version>20.4.251</version>
<version>20.5.21-beta</version>
<version>20.6.139</version>
<version>21.0.167</version>
<version>21.1.216</version>
<version>21.2.1-beta</version>
<version>21.3.95</version>
<version>21.4.156</version>
<version>21.5.96</version>
<version>21.6.20-beta</version>
<version>21.7.25-beta</version>
<version>21.8.52</version>
<version>21.9.16-beta</version>
<version>21.10.63</version>
<version>21.11.6-beta</version>
</versions>
<lastUpdated>20251212004616</lastUpdated>
</versioning>
</metadata>`;

const parser = new XMLParser();
// let data = parser.parse(await response.text());
let data = parser.parse(text);

const versions = data.metadata.versioning.versions.version
    .filter(version => !version.includes("w")) // filter out weird snapshot versions like 0.25w14craftmine.3-beta
    .filter(version => !version.includes("beta"));

const chunked = chunkify(versions, 256); // 256 is the max size of a gh actions matrix
let output = [];

chunked.forEach(chunk => {
    output.push(JSON.stringify(chunk));
})

process.stdout.write(JSON.stringify(output));

