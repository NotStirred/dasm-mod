const { XMLParser } = require("fast-xml-parser");


const response = await fetch("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml");
if (!response.ok) {
    console.error(`Neoforge versions response status: ${response.status}`);
    exit(1);
}

const parser = new XMLParser();
let data = parser.parse(response.text());

console.log(data)

const output = data.metadata.versioning.versions
    .map(node => node.version)
    .filter(version => !version.includes("w")) // filter out weird snapshot versions like 0.25w14craftmine.3-beta

console.log(output)
