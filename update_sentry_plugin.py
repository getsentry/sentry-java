#!/usr/bin/env python3
"""
Script to update Sentry Android Gradle Plugin versions and publish to Maven Local.

This script:
1. Increments the VERSION_NAME in gradle.properties files
2. Publishes plugins to Maven Local
3. Updates the checkmate app build.gradle with new versions
"""

import os
import re
import subprocess
import sys
from pathlib import Path

# File paths
KOTLIN_COMPILER_GRADLE_PROPERTIES = "/Users/markushi/sentry/sentry-android-gradle-plugin/sentry-kotlin-compiler-plugin/gradle.properties"
PLUGIN_BUILD_GRADLE_PROPERTIES = "/Users/markushi/sentry/sentry-android-gradle-plugin/plugin-build/gradle.properties"
CHECKMATE_BUILD_GRADLE = "/Users/markushi/p/checkmate/app/build.gradle"

# Directory paths for gradle commands
KOTLIN_COMPILER_DIR = "/Users/markushi/sentry/sentry-android-gradle-plugin/sentry-kotlin-compiler-plugin"
PLUGIN_BUILD_DIR = "/Users/markushi/sentry/sentry-android-gradle-plugin/plugin-build"
CHECKMATE_APP_DIR = "/Users/markushi/p/checkmate/app/"

def increment_version(version_string):
    """
    Increment the patch version of a version string.
    Example: 5.8.0-mah-018 -> 5.8.0-mah-019
    """
    # Pattern to match version like 5.8.0-mah-018
    pattern = r'(\d+\.\d+\.\d+-\w+-?)(\d+)'
    match = re.search(pattern, version_string)
    
    if match:
        prefix = match.group(1)
        number = int(match.group(2))
        new_number = number + 1
        return f"{prefix}{new_number:03d}"
    else:
        # Fallback: just increment the last number found
        pattern = r'(\d+)$'
        match = re.search(pattern, version_string)
        if match:
            number = int(match.group(1))
            new_number = number + 1
            return re.sub(r'\d+$', str(new_number), version_string)
        else:
            raise ValueError(f"Could not parse version string: {version_string}")

def update_gradle_properties(file_path, version_key, new_version):
    """Update a gradle.properties file with a new version."""
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"File not found: {file_path}")
    
    with open(file_path, 'r') as f:
        content = f.read()
    
    # Update the version line
    pattern = rf'^{version_key}\s*=\s*.*$'
    replacement = f'{version_key} = {new_version}'
    new_content = re.sub(pattern, replacement, content, flags=re.MULTILINE)
    
    if new_content == content:
        print(f"Warning: No {version_key} found in {file_path}")
        return False
    
    with open(file_path, 'w') as f:
        f.write(new_content)
    
    print(f"Updated {file_path}: {version_key} = {new_version}")
    return True

def get_current_version(file_path, version_key):
    """Get the current version from a gradle.properties file."""
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"File not found: {file_path}")
    
    with open(file_path, 'r') as f:
        content = f.read()
    
    pattern = rf'^{version_key}\s*=\s*(.+)$'
    match = re.search(pattern, content, re.MULTILINE)
    
    if match:
        return match.group(1).strip()
    else:
        raise ValueError(f"Could not find {version_key} in {file_path}")

def run_gradle_command(directory, command, print_output=False):
    """Run a gradle command in the specified directory."""
    print(f"Running '{command}' in {directory}")
    
    try:
        result = subprocess.run(
            command,
            cwd=directory,
            shell=True,
            check=True,
            capture_output=True,
            text=True
        )
        print(f"✓ Command completed successfully")
        if print_output:
            print(result.stdout)
        return True
    except subprocess.CalledProcessError as e:
        print(f"✗ Command failed with exit code {e.returncode}")
        print(f"stdout: {e.stdout}")
        print(f"stderr: {e.stderr}")
        return False

def update_checkmate_build_gradle(file_path, new_version):
    """Update the checkmate app build.gradle with new plugin versions."""
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"File not found: {file_path}")
    
    with open(file_path, 'r') as f:
        content = f.read()
    
    # Update both plugin versions
    patterns = [
        (r'id "io\.sentry\.android\.gradle" version "[^"]*"', 
         f'id "io.sentry.android.gradle" version "{new_version}"'),
        (r'id "io\.sentry\.kotlin\.compiler\.gradle" version "[^"]*"', 
         f'id "io.sentry.kotlin.compiler.gradle" version "{new_version}"')
    ]
    
    new_content = content
    updated = False
    
    for pattern, replacement in patterns:
        if re.search(pattern, new_content):
            new_content = re.sub(pattern, replacement, new_content)
            updated = True
    
    if not updated:
        print(f"Warning: No Sentry plugin versions found in {file_path}")
        return False
    
    with open(file_path, 'w') as f:
        f.write(new_content)
    
    print(f"Updated {file_path} with version {new_version}")
    return True

def main():
    """Main function to orchestrate the version update process."""
    try:
        # Step 1: Get current versions and increment them
        print("=== Step 1: Getting current versions ===")
        
        current_kotlin_version = get_current_version(KOTLIN_COMPILER_GRADLE_PROPERTIES, "VERSION_NAME")
        current_plugin_version = get_current_version(PLUGIN_BUILD_GRADLE_PROPERTIES, "version")
        
        print(f"Current Kotlin compiler version: {current_kotlin_version}")
        print(f"Current plugin build version: {current_plugin_version}")
        
        # Check if versions are the same
        if current_kotlin_version != current_plugin_version:
            print("Warning: Versions are different between the two gradle.properties files")
        
        # Increment version (use the kotlin compiler version as reference)
        new_version = increment_version(current_kotlin_version)
        print(f"New version: {new_version}")
        
        # Step 2: Update gradle.properties files
        print("\n=== Step 2: Updating gradle.properties files ===")
        
        update_gradle_properties(KOTLIN_COMPILER_GRADLE_PROPERTIES, "VERSION_NAME", new_version)
        update_gradle_properties(PLUGIN_BUILD_GRADLE_PROPERTIES, "version", new_version)
        
        # Step 3: Publish to Maven Local
        print("\n=== Step 3: Publishing to Maven Local ===")
        
        success1 = run_gradle_command(KOTLIN_COMPILER_DIR, "../gradlew publishToMavenLocal")
        success2 = run_gradle_command(PLUGIN_BUILD_DIR, "../gradlew publishToMavenLocal")
        
        if not (success1 and success2):
            print("✗ One or more gradle commands failed. Stopping.")
            sys.exit(1)
        
        # Step 4: Update checkmate app build.gradle
        print("\n=== Step 4: Updating checkmate app build.gradle ===")
        
        update_checkmate_build_gradle(CHECKMATE_BUILD_GRADLE, new_version)

        # Step 5: Sync the project
        print("\n=== Step 5: Building the app project ===")
        run_gradle_command(CHECKMATE_APP_DIR, "../gradlew --console=plain --no-daemon -Dorg.gradle.debug=true -Dkotlin.compiler.execution.strategy=\"in-process\" -Dkotlin.daemon.jvm.options=\"-Xdebug,-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=n\" assembleDebug")
        
        print(f"\n✓ All steps completed successfully!")
        print(f"✓ Version updated to: {new_version}")
        print(f"✓ Plugins published to Maven Local")
        print(f"✓ Checkmate app updated with new version")
        
    except Exception as e:
        print(f"✗ Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main() 