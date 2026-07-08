#!/usr/bin/env python3

import json
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import requests
from packaging import version


def get_spring_boot_versions():
    """Fetch all Spring Boot versions from Maven Central with retry logic."""
    max_retries = 3
    timeout = 60

    for attempt in range(max_retries):
        try:
            print(f"Fetching versions (attempt {attempt + 1}/{max_retries})...")

            metadata_url = "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/maven-metadata.xml"
            response = requests.get(metadata_url, timeout=timeout)

            if response.status_code == 200:
                print("Using Maven metadata XML approach...")
                root = ET.fromstring(response.text)
                versions = []
                versioning = root.find("versioning")
                if versioning is not None:
                    versions_element = versioning.find("versions")
                    if versions_element is not None:
                        for version_elem in versions_element.findall("version"):
                            candidate = version_elem.text
                            if is_supported_version(candidate):
                                versions.append(candidate)

                if versions:
                    print(f"Found {len(versions)} versions via XML")
                    print(f"Sample versions: {versions[-10:] if len(versions) > 10 else versions}")
                    valid_versions = parseable_versions(versions)
                    print(f"Filtered to {len(valid_versions)} valid versions")
                    return sorted(valid_versions, key=version.parse)

            print("Trying search API fallback...")
            search_url = "https://search.maven.org/solrsearch/select"
            params = {
                "q": 'g:"org.springframework.boot" AND a:"spring-boot"',
                "core": "gav",
                "rows": 1000,
                "wt": "json",
            }

            response = requests.get(search_url, params=params, timeout=timeout)
            response.raise_for_status()
            data = response.json()

            if "response" not in data or "docs" not in data["response"]:
                raise Exception("Unexpected API response structure")

            docs = data["response"]["docs"]
            print(f"Found {len(docs)} documents in search response")

            if docs:
                print(f"Sample doc structure: {list(docs[0].keys())}")

            versions = []
            for doc in docs:
                candidate = doc.get("v") or doc.get("version")
                if is_supported_version(candidate):
                    versions.append(candidate)

            if versions:
                valid_versions = parseable_versions(versions)
                print(f"Successfully fetched {len(valid_versions)} valid versions via search API")
                return sorted(valid_versions, key=version.parse)

        except Exception as e:
            print(f"Attempt {attempt + 1} failed: {e}")
            if attempt < max_retries - 1:
                print("Retrying...")
                continue

    print("All attempts failed")
    return []


def is_supported_version(candidate):
    return (
        candidate
        and not any(suffix in candidate for suffix in ["SNAPSHOT", "RC", "BUILD", "RELEASE"])
        and candidate[0].isdigit()
        and candidate.count(".") >= 2
    )


def parseable_versions(versions):
    valid_versions = []
    for candidate in versions:
        try:
            version.parse(candidate)
            valid_versions.append(candidate)
        except Exception:
            print(f"Skipping invalid version format: {candidate}")
    return valid_versions


def parse_current_versions(json_file):
    """Parse current Spring Boot versions from JSON data file."""
    if not Path(json_file).exists():
        return []

    try:
        with open(json_file) as f:
            data = json.load(f)
        return data.get("versions", [])
    except Exception as e:
        print(f"Error reading {json_file}: {e}")
        return []


def get_latest_patch(all_versions, minor_version):
    """Get the latest patch version for a given minor version."""
    target_minor = ".".join(minor_version.split(".")[:2])
    patches = [v for v in all_versions if v.startswith(target_minor + ".")]
    return max(patches, key=version.parse) if patches else minor_version


def update_version_matrix(current_versions, all_versions, major_version):
    """Update version matrix based on available versions."""
    if not current_versions or not all_versions:
        return current_versions, False

    major_versions = [v for v in all_versions if v.startswith(f"{major_version}.")]
    if not major_versions:
        return current_versions, False

    updated_versions = []
    changes_made = False

    min_version = current_versions[0]
    updated_versions.append(min_version)

    for curr_version in current_versions[1:]:
        if any(suffix in curr_version for suffix in ["M", "RC", "SNAPSHOT"]):
            updated_versions.append(curr_version)
            continue

        latest_patch = get_latest_patch(major_versions, curr_version)
        if latest_patch != curr_version:
            print(f"Updating {curr_version} -> {latest_patch}")
            changes_made = True
        updated_versions.append(latest_patch)

    current_minors = set()
    for candidate in current_versions:
        if not any(suffix in candidate for suffix in ["M", "RC", "SNAPSHOT"]):
            current_minors.add(".".join(candidate.split(".")[:2]))

    available_minors = set()
    for candidate in major_versions:
        if not any(suffix in candidate for suffix in ["M", "RC", "SNAPSHOT"]):
            available_minors.add(".".join(candidate.split(".")[:2]))

    new_minors = available_minors - current_minors
    if new_minors:
        for new_minor in sorted(new_minors, key=version.parse):
            latest_patch = get_latest_patch(major_versions, new_minor + ".0")
            updated_versions.append(latest_patch)
            print(f"Adding new minor version: {latest_patch}")
            changes_made = True

        if len(updated_versions) > 7:
            sorted_versions = sorted(updated_versions, key=version.parse)
            min_version = sorted_versions[0]
            other_versions = sorted_versions[1:]

            if len(other_versions) > 6:
                updated_versions = [min_version] + other_versions[1:]
                print(f"Removed second oldest version: {other_versions[0]}")
                changes_made = True

    min_version = updated_versions[0]
    other_versions = sorted(
        [candidate for candidate in updated_versions if candidate != min_version],
        key=version.parse,
    )
    final_versions = [min_version] + other_versions

    seen = set()
    deduplicated_versions = []
    for candidate in final_versions:
        if candidate not in seen:
            seen.add(candidate)
            deduplicated_versions.append(candidate)

    if len(deduplicated_versions) != len(final_versions):
        print(f"Removed {len(final_versions) - len(deduplicated_versions)} duplicate versions")

    return deduplicated_versions, changes_made


def update_json_file(json_file, new_versions):
    """Update the JSON data file with new versions."""
    try:
        data = {"versions": new_versions}
        with open(json_file, "w") as f:
            json.dump(data, f, indent=2, separators=(",", ": "))
            f.write("\n")
        return True
    except Exception as e:
        print(f"Error writing to {json_file}: {e}")
        return False


def write_github_output(change_summary):
    output_file = os.environ.get("GITHUB_OUTPUT")
    if not output_file:
        return

    with open(output_file, "a") as f:
        f.write("changes_summary<<EOF\n")
        f.write("\n".join(change_summary))
        f.write("\nEOF\n")


def main():
    print("Fetching Spring Boot versions...")
    all_versions = get_spring_boot_versions()

    if not all_versions:
        print("No versions found, exiting")
        return 1

    print(f"Found {len(all_versions)} versions")

    data_files = [
        (".github/data/spring-boot-2-versions.json", "2"),
        (".github/data/spring-boot-3-versions.json", "3"),
        (".github/data/spring-boot-4-versions.json", "4"),
    ]

    changes_made = False
    change_summary = []

    for json_file, major_version in data_files:
        if not Path(json_file).exists():
            continue

        print(f"\nProcessing {json_file} (Spring Boot {major_version}.x)")

        current_versions = parse_current_versions(json_file)
        if not current_versions:
            continue

        print(f"Current versions: {current_versions}")

        new_versions, file_changed = update_version_matrix(
            current_versions, all_versions, major_version
        )

        if file_changed:
            print(f"New versions: {new_versions}")
            if update_json_file(json_file, new_versions):
                changes_made = True
                change_summary.append(
                    f"Spring Boot {major_version}.x: {current_versions} -> {new_versions}"
                )
        else:
            print("No changes needed")

    if changes_made:
        print("\nChanges made to Spring Boot version files:")
        for change in change_summary:
            print(f"  - {change}")
        write_github_output(change_summary)
    else:
        print("\nNo version updates needed")

    return 0


if __name__ == "__main__":
    sys.exit(main())
