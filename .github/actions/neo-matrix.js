#!/usr/bin/env node
import { XMLParser } from "fast-xml-parser";

const response = await fetch("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml");
if (!response.ok) {
    // noinspection ExceptionCaughtLocallyJS
    throw new Error(`Neoforge versions response status: ${response.status}`);
}

const text = `<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>net.neoforged</groupId>
  <artifactId>neoforge</artifactId>
  <versioning>
    <latest>21.11.6-beta</latest>
    <release>21.11.6-beta</release>
    <versions>
      <version>21.11.5-beta</version>
      <version>21.11.6-beta</version>
    </versions>
    <lastUpdated>20251212004616</lastUpdated>
  </versioning>
</metadata>
`;

const parser = new XMLParser();
let data = parser.parse(text);

const output = data.metadata.versioning.versions.version
    .filter(version => !version.includes("w")) // filter out weird snapshot versions like 0.25w14craftmine.3-beta

process.stdout.write("'" + JSON.stringify(output) + "'")

