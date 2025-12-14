import { XMLParser } from "fast-xml-parser";

try {
    const response = await fetch("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml");
    if (!response.ok) {
        // noinspection ExceptionCaughtLocallyJS
        throw new Error(`Neoforge versions response status: ${response.status}`);
    }

    const parser = new XMLParser();
    let data = parser.parse(await response.text());

    process.stdout.write(JSON.stringify(data))

    const output = data.metadata.versioning.versions
        .map(node => node.version)
        .filter(version => !version.includes("w")) // filter out weird snapshot versions like 0.25w14craftmine.3-beta

    process.stdout.write(output)

} catch (e) {
    console.error(e);
    process.exit(1)
}
