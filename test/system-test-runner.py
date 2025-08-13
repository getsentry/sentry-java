#!/usr/bin/env python3

"""
System Test Runner for Sentry Java

Usage examples:
  # Run all tests
  python3 test/system-test-runner.py test --all

  # Run specific module test
  python3 test/system-test-runner.py test --module sentry-samples-console

  # Set up infrastructure for manual testing from IDE
  python3 test/system-test-runner.py test --module sentry-samples-console --manual-test

  # Start Sentry mock server
  python3 test/system-test-runner.py sentry start

  # Start Spring app served by Tomcat
  python3 test/system-test-runner.py tomcat start sentry-samples-spring

  # Start Spring Boot app
  python3 test/system-test-runner.py spring start sentry-samples-spring-boot

  # Start Spring Boot app with build
  python3 test/system-test-runner.py spring start sentry-samples-spring-boot --build

  # Check status of all services
  python3 test/system-test-runner.py status

  # Stop services
  python3 test/system-test-runner.py sentry stop
  python3 test/system-test-runner.py spring stop
"""

import subprocess
import sys
import time
import signal
import os
from enum import Enum
import argparse
import requests
import threading
from pathlib import Path
from typing import Optional, List, Tuple
from dataclasses import dataclass

TERMINAL_COLUMNS: int = 60
try:
    TERMINAL_COLUMNS: int = os.get_terminal_size().columns
except:
    pass

SENTRY_ENVIRONMENT_VARIABLES = {
    "SENTRY_DSN": "http://502f25099c204a2fbf4cb16edc5975d1@localhost:8000/0",
    "SENTRY_TRACES_SAMPLE_RATE": "1.0",
    "OTEL_TRACES_EXPORTER": "none",
    "OTEL_METRICS_EXPORTER": "none",
    "OTEL_LOGS_EXPORTER": "none",
    "SENTRY_LOGS_ENABLED": "true"
}

class ServerType(Enum):
    TOMCAT = 0
    SPRING = 1

def str_to_bool(value: str) -> str:
    """Convert true/false string to 1/0 string for internal compatibility."""
    if value.lower() in ('true', '1'):
        return "1"
    elif value.lower() in ('false', '0'):
        return "0"
    else:
        raise ValueError(f"Invalid boolean value: {value}. Use 'true' or 'false'")

@dataclass
class Server:
    name: str
    pid_filepath: str
    process: Optional[subprocess.Popen] = None
    pid: Optional[int] = None

def store_pid(server: Server):
    # Store PID in instance variable and write to file
    server.pid = server.process.pid
    with open(server.pid_filepath, "w") as pid_file:
        pid_file.write(str(server.pid))

def cleanup_pid(server: Server):
    # Clean up PID file and instance variable
    if os.path.exists(server.pid_filepath):
        os.remove(server.pid_filepath)
    server.pid = None

@dataclass
class ModuleConfig:
    """Configuration for a test module."""
    name: str
    java_agent: str
    java_agent_auto_init: str
    build_before_run: str

    def uses_agent(self) -> bool:
        """Check if this module uses the Java agent."""
        return str_to_bool(self.java_agent) == "1"

    def needs_build(self) -> bool:
        """Check if this module needs to be built before running."""
        return str_to_bool(self.build_before_run) == "1"

    def is_spring_module(self) -> bool:
        """Check if this is a Spring Boot module."""
        return "spring" in self.name

@dataclass
class InteractiveSelection:
    """Result of interactive module selection."""
    modules: List[ModuleConfig]
    manual_test_mode: bool
    build_agent: bool

    def is_empty(self) -> bool:
        """Check if no modules were selected."""
        return len(self.modules) == 0

    def is_single_module(self) -> bool:
        """Check if exactly one module was selected."""
        return len(self.modules) == 1

    def get_first_module(self) -> ModuleConfig:
        """Get the first selected module (for manual test mode)."""
        if self.is_empty():
            raise ValueError("No modules selected")
        return self.modules[0]

    def has_agent_modules(self) -> bool:
        """Check if any selected modules use the Java agent."""
        return any(module_config.uses_agent() for module_config in self.modules)

class SystemTestRunner:
    def __init__(self):
        self.mock_server = Server(name="Mock", pid_filepath="sentry-mock-server.pid")
        self.tomcat_server = Server(name="Tomcat", pid_filepath="tomcat-server.pid")
        self.spring_server = Server(name="Spring", pid_filepath="spring-server.pid")

        # Load existing PIDs if available
        for server in (self.mock_server, self.tomcat_server, self.spring_server):
            server.pid = self.read_pid_file(server.pid_filepath)
            if server.pid:
                print(f"Found existing {server.name} server PID: {server.pid}")

    def read_pid_file(self, pid_file: str) -> Optional[int]:
        """Read PID from file if it exists."""
        try:
            if os.path.exists(pid_file):
                with open(pid_file, 'r') as f:
                    return int(f.read().strip())
        except (ValueError, IOError) as e:
            print(f"Error reading PID file {pid_file}: {e}")
        return None

    def is_process_running(self, pid: int) -> bool:
        """Check if a process with given PID is still running."""
        try:
            # Send signal 0 to check if process exists
            os.kill(pid, 0)
            return True
        except (OSError, ProcessLookupError):
            return False

    def kill_process(self, pid: int, name: str) -> None:
        """Kill a process by PID."""
        try:
            print(f"Killing existing {name} process with PID {pid}")
            os.kill(pid, signal.SIGTERM)
            time.sleep(2)  # Give it time to terminate gracefully

            # Check if it's still running and force kill if necessary
            if self.is_process_running(pid):
                print(f"Process {pid} didn't terminate gracefully, force killing...")
                os.kill(pid, signal.SIGKILL)
                time.sleep(1)
        except (OSError, ProcessLookupError):
            print(f"Process {pid} was already dead")



    def start_sentry_mock_server(self) -> None:
        """Start the Sentry mock server."""
        print("Starting Sentry mock server...")
        try:
            # Start the mock server in the background
            with open("sentry-mock-server.txt", "w") as log_file:
                self.mock_server.process = subprocess.Popen(
                    ["python3", "test/system-test-sentry-server.py"],
                    stdout=log_file,
                    stderr=subprocess.STDOUT
                )

            # Store PID in instance variable and write to file
            store_pid(self.mock_server)
            print(f"Started mock server with PID {self.mock_server.pid}")

            # Wait a moment for the server to start
            time.sleep(2)

        except Exception as e:
            print(f"Failed to start mock server: {e}")
            raise

    def stop_sentry_mock_server(self) -> None:
        """Stop the Sentry mock server."""
        try:
            # Try graceful shutdown first
            try:
                response = requests.get("http://127.0.0.1:8000/STOP", timeout=5)
                print("Sent stop signal to mock server")
            except:
                print("Could not send graceful stop signal")

            # Kill the process - try process object first, then PID from file
            if self.mock_server.process and self.mock_server.process.poll() is None:
                print(f"Killing mock server process object with PID {self.mock_server.process.pid}")
                self.mock_server.process.kill()
                self.mock_server.process.wait(timeout=5)
            elif self.mock_server.pid and self.is_process_running(self.mock_server.pid):
                print(f"Killing mock server from PID file with PID {self.mock_server.pid}")
                self.kill_process(self.mock_server.pid, "mock server")

        except Exception as e:
            print(f"Error stopping mock server: {e}")
        finally:
            # Clean up PID file and instance variable
            cleanup_pid(self.mock_server)

    def find_agent_jar(self) -> Optional[str]:
        """Find the OpenTelemetry agent JAR file."""
        agent_dir = Path("sentry-opentelemetry/sentry-opentelemetry-agent/build/libs/")
        if not agent_dir.exists():
            return None

        for jar_file in agent_dir.glob("*.jar"):
            name = jar_file.name
            if ("agent" in name and
                "javadoc" not in name and
                "sources" not in name and
                "dontuse" not in name):
                return str(jar_file)
        return None

    def build_agent_jar(self) -> int:
        """Build the OpenTelemetry agent JAR file."""
        print("Building OpenTelemetry agent JAR...")
        return self.run_gradle_task(":sentry-opentelemetry:sentry-opentelemetry-agent:assemble")

    def ensure_agent_jar(self, skip_build: bool = False) -> Optional[str]:
        """Ensure the OpenTelemetry agent JAR exists, building it if necessary."""
        agent_jar = self.find_agent_jar()
        if agent_jar:
            return agent_jar

        if skip_build:
            print("OpenTelemetry agent JAR not found and build was skipped")
            return None

        # Agent JAR doesn't exist, try to build it
        print("OpenTelemetry agent JAR not found, building it...")
        build_result = self.build_agent_jar()
        if build_result != 0:
            print("Failed to build OpenTelemetry agent JAR")
            return None

        # Try to find it again after building
        agent_jar = self.find_agent_jar()
        if not agent_jar:
            print("OpenTelemetry agent JAR still not found after building")
            return None

        return agent_jar

    def start_tomcat_server(self, sample_module: str) -> None:
        # Build environment variables
        env = os.environ.copy()
        env.update(SENTRY_ENVIRONMENT_VARIABLES)

        try:
            # Start the Tomcat server
            with open("tomcat-server.txt", "w") as log_file:
                self.tomcat_server.process = subprocess.Popen(
                    ["./gradlew", f"sentry-samples:{sample_module}:run"],
                    env=env,
                    stdout=log_file,
                    stderr=subprocess.STDOUT
                )

                store_pid(self.tomcat_server)
                print(f"Started Tomcat server with PID {self.tomcat_server.pid}")
        except Exception as e:
            print(f"Failed to start Tomcat server: {e}")
            raise

    def start_spring_server(self, sample_module: str, java_agent: str, java_agent_auto_init: str) -> None:
        """Start a Spring Boot server for testing."""
        print(f"Starting Spring server for {sample_module}...")

        # Build environment variables
        env = os.environ.copy()
        env.update(SENTRY_ENVIRONMENT_VARIABLES)
        env["SENTRY_AUTO_INIT"] = java_agent_auto_init

        # Build command
        jar_path = f"sentry-samples/{sample_module}/build/libs/{sample_module}-0.0.1-SNAPSHOT.jar"
        cmd = ["java"]

        if java_agent == "1":
            agent_jar = self.ensure_agent_jar()
            if agent_jar:
                cmd.append(f"-javaagent:{agent_jar}")
                print(f"Using Java Agent: {agent_jar}")
            else:
                print("Warning: Java agent was requested but could not be found or built")

        cmd.extend(["-jar", jar_path])

        try:
            # Start the Spring server
            with open("spring-server.txt", "w") as log_file:
                self.spring_server.process = subprocess.Popen(
                    cmd,
                    env=env,
                    stdout=log_file,
                    stderr=subprocess.STDOUT
                )

            # Store PID in instance variable and write to file
            store_pid(self.spring_server)
            print(f"Started Spring server with PID {self.spring_server.pid}")

        except Exception as e:
            print(f"Failed to start Spring server: {e}")
            raise

    def wait_for_tomcat(self, module_name: str, max_attempts: int = 20) -> bool:
        """Wait for Tomcat to be ready."""
        print("Waiting for Tomcat to be ready...")

        for attempt in range(1, max_attempts + 1):
            try:
                response = requests.head(
                    f"http://localhost:8080/{module_name}-0.0.1-SNAPSHOT/person/1",
                    timeout=5
                )
                if response.status_code != 404:
                    print("Tomcat is ready!")
                    return True
            except:
                pass

            print(f"Waiting... (attempt {attempt}/{max_attempts})")
            time.sleep(1)

        print("Tomcat failed to become ready")
        return False

    def wait_for_spring(self, max_attempts: int = 20) -> bool:
        """Wait for Spring Boot application to be ready."""
        print("Waiting for Spring application to be ready...")

        for attempt in range(1, max_attempts + 1):
            try:
                response = requests.head(
                    "http://localhost:8080/actuator/health",
                    auth=("user", "password"),
                    timeout=5
                )
                if response.status_code == 200:
                    print("Spring application is ready!")
                    return True
            except:
                pass

            print(f"Waiting... (attempt {attempt}/{max_attempts})")
            time.sleep(1)

        print("Spring application failed to become ready")
        return False

    def get_spring_status(self) -> dict:
        """Get status of Spring Boot application."""
        status = {
            "process_running": False,
            "pid": self.spring_server.pid,
            "http_ready": False
        }

        if self.spring_server.pid and self.is_process_running(self.spring_server.pid):
            status["process_running"] = True

        # Check HTTP endpoint
        try:
            response = requests.head(
                "http://localhost:8080/actuator/health",
                auth=("user", "password"),
                timeout=2
            )
            if response.status_code == 200:
                status["http_ready"] = True
        except:
            pass

        return status

    def get_sentry_status(self) -> dict:
        """Get status of Sentry mock server."""
        status = {
            "process_running": False,
            "pid": self.mock_server.pid,
            "http_ready": False
        }

        if self.mock_server.pid and self.is_process_running(self.mock_server.pid): 
            status["process_running"] = True

        # Check HTTP endpoint
        try:
            response = requests.get("http://127.0.0.1:8000/envelope-count", timeout=2)
            if response.status_code == 200:
                status["http_ready"] = True
        except:
            pass

        return status

    def print_status_summary(self) -> None:
        """Print status summary of all services."""
        print("=== Service Status ===")

        sentry_status = self.get_sentry_status()
        print(f"Sentry Mock Server:")
        print(f"  PID: {sentry_status['pid'] or 'None'}")
        print(f"  Process Running: {'✅' if sentry_status['process_running'] else '❌'}")
        print(f"  HTTP Ready: {'✅' if sentry_status['http_ready'] else '❌'}")

        spring_status = self.get_spring_status()
        print(f"Spring Boot App:")
        print(f"  PID: {spring_status['pid'] or 'None'}")
        print(f"  Process Running: {'✅' if spring_status['process_running'] else '❌'}")
        print(f"  HTTP Ready: {'✅' if spring_status['http_ready'] else '❌'}")

    def stop_tomcat_server(self) -> None:
        """Stop the Tomcat server."""
        try:
            # Kill the process - try process object first, then PID from file
            if self.tomcat_server.process and self.tomcat_server.process.poll() is None:
                print(f"Killing Tomcat server process object with PID {self.tomcat_server.process.pid}")
                self.tomcat_server.process.kill()
                try:
                    self.tomcat_server.process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    print("Tomcat server did not terminate gracefully")
            elif self.tomcat_server.pid and self.is_process_running(self.tomcat_server.pid):
                print(f"Killing Tomcat server from PID file with PID {self.tomcat_server.pid}")
                self.kill_process(self.tomcat_server.pid, "Tomcat server")

        except Exception as e:
            print(f"Error stopping Tomcat server: {e}")
        finally:
            # Clean up PID file and instance variable
            cleanup_pid(self.tomcat_server)

    def stop_spring_server(self) -> None:
        """Stop the Spring Boot server."""
        try:
            # Kill the process - try process object first, then PID from file
            if self.spring_server.process and self.spring_server.process.poll() is None:
                print(f"Killing Spring server process object with PID {self.spring_server.process.pid}")
                self.spring_server.process.kill()
                try:
                    self.spring_server.process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    print("Spring server did not terminate gracefully")
            elif self.spring_server.pid and self.is_process_running(self.spring_server.pid):
                print(f"Killing Spring server from PID file with PID {self.spring_server.pid}")
                self.kill_process(self.spring_server.pid, "Spring server")

        except Exception as e:
            print(f"Error stopping Spring server: {e}")
        finally:
            # Clean up PID file and instance variable
            cleanup_pid(self.spring_server)

    def get_build_task(self, server_type: Optional[ServerType]) -> str:
        """Get the appropriate build task for a module."""
        if server_type == ServerType.TOMCAT:
            return "war"
        elif server_type == ServerType.SPRING:
            return "bootJar"

        return "assemble"

    def build_module(self, sample_module: str, server_type: Optional[ServerType]) -> int:
        """Build a sample module using the appropriate task."""
        build_task = self.get_build_task(server_type)
        print(f"Building {sample_module} using {build_task} task")
        return self.run_gradle_task(f":sentry-samples:{sample_module}:{build_task}")

    def run_gradle_task(self, task: str) -> int:
        """Run a Gradle task and return the exit code."""
        print(f"Running: ./gradlew {task}")
        try:
            result = subprocess.run(["./gradlew", task], check=False)
            return result.returncode
        except Exception as e:
            print(f"Failed to run Gradle task: {e}")
            return 1

    def setup_test_infrastructure(self, sample_module: str, java_agent: str,
                                 java_agent_auto_init: str, build_before_run: str,
                                 server_type: Optional[ServerType]) -> int:
        """Set up test infrastructure. Returns 0 on success, error code on failure."""
        # Build if requested
        if build_before_run == "1":
            print("Building before test run")
            build_result = self.build_module(sample_module, server_type)
            if build_result != 0:
                print("Build failed")
                return build_result

        # Ensure agent JAR is available if needed
        if java_agent == "1":
            agent_jar = self.ensure_agent_jar()
            if not agent_jar:
                print("Error: Java agent was requested but could not be found or built")
                return 1

        # Start mock server
        print("Starting Sentry mock server...")
        self.start_sentry_mock_server()

        # Start Tomcat server given a Spring module
        if server_type == ServerType.TOMCAT:
            print(f"Starting Tomcat server for {sample_module}...")
            self.start_tomcat_server(sample_module)
            if not self.wait_for_tomcat(sample_module):
                print("Tomcat application failed to start!")
                return 1
            print("Tomcat application is ready!")
        # Start Spring server if it's a Spring boot module
        elif server_type == ServerType.SPRING:
            print(f"Starting Spring server for {sample_module}...")
            self.start_spring_server(sample_module, java_agent, java_agent_auto_init)
            if not self.wait_for_spring():
                print("Spring application failed to start!")
                return 1
            print("Spring application is ready!")

        return 0

    def run_single_test(self, sample_module: str, java_agent: str,
                       java_agent_auto_init: str, build_before_run: str) -> int:
        """Run a single system test."""
        print(f"Running system test for {sample_module}")

        server_type = None
        if "spring" in sample_module and "spring-boot" not in sample_module:
            server_type = ServerType.TOMCAT
        elif "spring" in sample_module:
            server_type = ServerType.SPRING

        try:
            # Set up infrastructure
            setup_result = self.setup_test_infrastructure(sample_module,
                                                          java_agent,
                                                          java_agent_auto_init,
                                                          build_before_run,
                                                          server_type)
            if setup_result != 0:
                return setup_result

            # Run the system test
            test_result = self.run_gradle_task(f":sentry-samples:{sample_module}:systemTest")

            return test_result

        finally:
            # Cleanup
            if server_type == ServerType.TOMCAT:
                self.stop_tomcat_server()
            elif server_type == ServerType.SPRING:
                self.stop_spring_server()
            self.stop_sentry_mock_server()

    def run_all_tests(self) -> int:
        """Run all system tests."""
        test_configs = self.get_available_modules()

        failed_tests = []

        for i, module_config in enumerate(test_configs):
            # Convert true/false to internal 1/0 format
            agent = str_to_bool(module_config.java_agent)
            auto_init = module_config.java_agent_auto_init  # already in correct format
            build = str_to_bool(module_config.build_before_run)

            print(f"\n{'='*TERMINAL_COLUMNS}")
            print(f"Running test {i + 1}/{len(test_configs)}: {module_config.name} (agent={module_config.java_agent}, auto_init={module_config.java_agent_auto_init})")
            print(f"{'='*TERMINAL_COLUMNS}")

            result = self.run_single_test(module_config.name, agent, auto_init, build)

            if result != 0:
                # Find the module number in the full list for interactive reference
                module_number = self._find_module_number(module_config.name, module_config.java_agent, module_config.java_agent_auto_init)
                failed_tests.append((module_number, module_config.name, module_config.java_agent, module_config.java_agent_auto_init))
                print(f"❌ Test failed: {module_config.name}")
            else:
                print(f"✅ Test passed: {module_config.name}")

        # Summary
        print(f"\n{'='*TERMINAL_COLUMNS}")
        print("TEST SUMMARY")
        print(f"{'='*TERMINAL_COLUMNS}")
        print(f"Total tests: {len(test_configs)}")
        print(f"Passed: {len(test_configs) - len(failed_tests)}")
        print(f"Failed: {len(failed_tests)}")

        if failed_tests:
            print("\nFailed tests (for interactive mode, use these numbers):")
            for module_number, sample_module, java_agent, java_agent_auto_init in failed_tests:
                print(f"  {module_number}. {sample_module} (agent={java_agent}, auto_init={java_agent_auto_init})")
            return 1
        else:
            print("\n🎉 All tests passed!")
            return 0

    def run_manual_test_mode(self, sample_module: str, java_agent: str,
                            java_agent_auto_init: str, build_before_run: str) -> int:
        """Set up infrastructure for manual testing from IDE."""
        print(f"Setting up manual test environment for {sample_module}")

        try:
            # Set up infrastructure
            setup_result = self.setup_test_infrastructure(sample_module, java_agent, java_agent_auto_init, build_before_run)
            if setup_result != 0:
                return setup_result

            # Show status and wait for user
            print("\n" + "="*TERMINAL_COLUMNS)
            print("🚀 Manual test environment ready 🚀")
            print("="*TERMINAL_COLUMNS)
            self.print_status_summary()
            print(f"\nInfrastructure is ready for manual testing of: {sample_module}")
            print("You can now run your system tests from your IDE.")
            print("\nTest configuration:")
            print(f"  - Module: {sample_module}")
            print(f"  - Java Agent: {'Yes' if java_agent == '1' else 'No'}")
            print(f"  - Agent Auto-init: {java_agent_auto_init}")
            print(f"  - Mock DSN: http://502f25099c204a2fbf4cb16edc5975d1@localhost:8000/0")

            if "spring" in sample_module:
                print("\nSpring Boot app is running on: http://localhost:8080")

            print("\nPress Enter to stop the infrastructure and exit...")

            # Wait for user input
            try:
                input()
            except KeyboardInterrupt:
                print("\nReceived interrupt signal")

            print("\nStopping infrastructure...")
            return 0

        finally:
            # Cleanup will happen in the finally block of main()
            pass

    def get_available_modules(self) -> List[ModuleConfig]:
        """Get list of all available test modules."""
        return [
            ModuleConfig("sentry-samples-spring-jakarta", "false", "true", "false"),
            ModuleConfig("sentry-samples-spring-boot", "false", "true", "false"),
            ModuleConfig("sentry-samples-spring-boot-opentelemetry-noagent", "false", "true", "false"),
            ModuleConfig("sentry-samples-spring-boot-opentelemetry", "true", "true", "false"),
            ModuleConfig("sentry-samples-spring-boot-opentelemetry", "true", "false", "false"),
            ModuleConfig("sentry-samples-spring-boot-webflux-jakarta", "false", "true", "false"),
            ModuleConfig("sentry-samples-spring-boot-webflux", "false", "true", "false"),
            ModuleConfig("sentry-samples-spring-boot-jakarta", "false", "true", "false"),
            ModuleConfig("sentry-samples-spring-boot-jakarta-opentelemetry-noagent", "false", "true", "false"),
            ModuleConfig("sentry-samples-spring-boot-jakarta-opentelemetry", "true", "true", "false"),
            ModuleConfig("sentry-samples-spring-boot-jakarta-opentelemetry", "true", "false", "false"),
            ModuleConfig("sentry-samples-console", "false", "true", "false"),
            ModuleConfig("sentry-samples-console-opentelemetry-noagent", "false", "true", "false"),
        ]

    def _find_module_number(self, module_name: str, agent: str, auto_init: str) -> int:
        """Find the module number in the interactive list (1-based)."""
        modules = self.get_available_modules()
        for i, module_config in enumerate(modules, 1):
            if (module_config.name == module_name and
                module_config.java_agent == agent and
                module_config.java_agent_auto_init == auto_init):
                return i
        return 0  # Should not happen, but return 0 if not found

    def parse_selection(self, user_input: str, max_index: int) -> List[int]:
        """Parse user selection string into list of indices."""
        if user_input.strip() == "*":
            return list(range(max_index))

        indices = []
        parts = user_input.split(",")

        for part in parts:
            part = part.strip()
            if "-" in part:
                # Handle range like "1-4"
                try:
                    start, end = map(int, part.split("-"))
                    # Convert from 1-based to 0-based indexing
                    indices.extend(range(start - 1, end))
                except ValueError:
                    raise ValueError(f"Invalid range format: {part}")
            else:
                # Handle single number
                try:
                    # Convert from 1-based to 0-based indexing
                    indices.append(int(part) - 1)
                except ValueError:
                    raise ValueError(f"Invalid number: {part}")

        # Remove duplicates and sort
        indices = sorted(set(indices))

        # Validate indices
        for idx in indices:
            if idx < 0 or idx >= max_index:
                raise ValueError(f"Index {idx + 1} is out of range (1-{max_index})")

        return indices

    def interactive_module_selection(self) -> InteractiveSelection:
        """Display modules and get user selection."""
        modules = self.get_available_modules()

        print("\nAvailable test modules:")
        print("=" * 80)
        for i, module_config in enumerate(modules, 1):
            agent_text = "with agent" if module_config.uses_agent() else "no agent"
            auto_init_text = f"auto-init: {module_config.java_agent_auto_init}"
            print(f"{i:2d}. {module_config.name:<50} ({agent_text}, {auto_init_text})")

        print("\nSelection options:")
        print("  * = all modules")
        print("  Single: 1, 5, 8")
        print("  Range: 1-4, 6-8")
        print("  Combined: 1,2,4-5,8")

        selected_modules = []
        while True:
            try:
                user_input = input("\nEnter your selection: ").strip()
                if not user_input:
                    print("Please enter a selection.")
                    continue

                selected_indices = self.parse_selection(user_input, len(modules))
                selected_modules = [modules[i] for i in selected_indices]

                # Show confirmation
                print(f"\nSelected {len(selected_modules)} module(s):")
                for i, module_config in enumerate(selected_modules, 1):
                    agent_text = "with agent" if module_config.uses_agent() else "no agent"
                    print(f"  {i}. {module_config.name} ({agent_text}, auto-init: {module_config.java_agent_auto_init})")

                confirm = input("\nProceed with these selections? [Y/n]: ").strip().lower()
                if confirm in ('', 'y', 'yes'):
                    break
                else:
                    print("Please make a new selection.")

            except ValueError as e:
                print(f"Error: {e}")
                print("Please try again.")
            except KeyboardInterrupt:
                print("\nOperation cancelled.")
                return InteractiveSelection(modules=[], manual_test_mode=False, build_agent=False)

        # Ask about test mode
        manual_test_mode = False
        while True:
            try:
                mode_input = input("\nRun tests automatically (n = only set up infrastucture for testing in IDE)? [Y/n]: ").strip().lower()
                if not mode_input or mode_input in ('y', 'yes'):
                    manual_test_mode = False
                    break
                elif mode_input in ('n', 'no'):
                    manual_test_mode = True
                    break
                else:
                    print("Please enter 'y' or 'n'.")
            except KeyboardInterrupt:
                print("\nOperation cancelled.")
                return InteractiveSelection(modules=[], manual_test_mode=False, build_agent=False)

        # Ask about building agent if any modules use it
        build_agent = False
        has_agent_modules = any(module_config.uses_agent() for module_config in selected_modules)
        if has_agent_modules:
            while True:
                try:
                    agent_input = input("\nBuild OpenTelemetry agent JAR (recommended to ensure latest version)? [Y/n]: ").strip().lower()
                    if not agent_input or agent_input in ('y', 'yes'):
                        build_agent = True
                        break
                    elif agent_input in ('n', 'no'):
                        build_agent = False
                        break
                    else:
                        print("Please enter 'y' or 'n'.")
                except KeyboardInterrupt:
                    print("\nOperation cancelled.")
                    return InteractiveSelection(modules=[], manual_test_mode=False, build_agent=False)

        return InteractiveSelection(modules=selected_modules, manual_test_mode=manual_test_mode, build_agent=build_agent)

    def run_interactive_tests(self, agent: str, auto_init: str, build: str) -> int:
        """Run tests with interactive module selection."""
        selection = self.interactive_module_selection()

        if selection.is_empty():
            print("No modules selected. Exiting.")
            return 0

        # Build agent JAR if requested and modules use agent
        if selection.build_agent and selection.has_agent_modules():
            print("\nBuilding OpenTelemetry agent JAR...")
            build_result = self.build_agent_jar()
            if build_result != 0:
                print("Failed to build OpenTelemetry agent JAR")
                return build_result
            print("✅ OpenTelemetry agent JAR built successfully")

        # Handle manual test mode
        if selection.manual_test_mode:
            if not selection.is_single_module():
                print("Error: Manual test mode can only be used with a single module.")
                print("Please select only one module for manual testing.")
                return 1

            module_config = selection.get_first_module()
            # Convert true/false to internal 1/0 format
            agent = str_to_bool(module_config.java_agent)
            auto_init = module_config.java_agent_auto_init  # already in correct format
            build = str_to_bool(module_config.build_before_run)

            print(f"\nSetting up manual test environment for: {module_config.name}")
            return self.run_manual_test_mode(module_config.name, agent, auto_init, build)

        # Handle automatic test running
        failed_tests = []

        for i, module_config in enumerate(selection.modules, 1):
            # Convert true/false to internal 1/0 format
            agent = str_to_bool(module_config.java_agent)
            auto_init = module_config.java_agent_auto_init  # already in correct format
            build = str_to_bool(module_config.build_before_run)

            print(f"\n{'='*TERMINAL_COLUMNS}")
            print(f"Running test {i}/{len(selection.modules)}: {module_config.name}")
            print(f"Agent: {module_config.java_agent}, Auto-init: {module_config.java_agent_auto_init}")
            print(f"{'='*TERMINAL_COLUMNS}")

            result = self.run_single_test(module_config.name, agent, auto_init, build)

            if result != 0:
                # Find the module number in the full list for interactive reference
                module_number = self._find_module_number(module_config.name, module_config.java_agent, module_config.java_agent_auto_init)
                failed_tests.append((module_number, module_config.name, module_config.java_agent, module_config.java_agent_auto_init))
                print(f"❌ Test failed: {module_config.name}")
            else:
                print(f"✅ Test passed: {module_config.name}")

        # Summary
        print(f"\n{'='*TERMINAL_COLUMNS}")
        print("TEST SUMMARY")
        print(f"{'='*TERMINAL_COLUMNS}")
        print(f"Total tests: {len(selection.modules)}")
        print(f"Passed: {len(selection.modules) - len(failed_tests)}")
        print(f"Failed: {len(failed_tests)}")

        if failed_tests:
            print("\nFailed tests (for interactive mode, use these numbers):")
            for module_number, sample_module, test_agent, test_auto_init in failed_tests:
                print(f"  {module_number}. {sample_module} (agent={test_agent}, auto_init={test_auto_init})")
            return 1
        else:
            print("\n🎉 All tests passed!")
            return 0

    def cleanup_on_exit(self, signum, frame):
        """Cleanup handler for signals."""
        print(f"\nReceived signal {signum}, cleaning up...")
        self.stop_spring_server()
        self.stop_sentry_mock_server()
        sys.exit(1)

def main():
    parser = argparse.ArgumentParser(description="System Test Runner for Sentry Java")
    subparsers = parser.add_subparsers(dest="command", help="Available commands")

    # Test subcommand
    test_parser = subparsers.add_parser("test", help="Run system tests")
    test_group = test_parser.add_mutually_exclusive_group(required=True)
    test_group.add_argument("--all", action="store_true", help="Run all system tests")
    test_group.add_argument("--module", help="Sample module to test")
    test_group.add_argument("--interactive", "-i", action="store_true", help="Interactive module selection")
    test_parser.add_argument("--agent", default="false", help="Use Java agent (true or false)")
    test_parser.add_argument("--auto-init", default="true", help="Auto-init agent (true or false)")
    test_parser.add_argument("--build", default="false", help="Build before running (true or false)")
    test_parser.add_argument("--manual-test", action="store_true", help="Set up infrastructure but pause for manual testing from IDE")

    # Tomcat subcommand
    tomcat_parser = subparsers.add_parser("tomcat", help="Manage Servlet applications deployed on Tomcat")
    tomcat_subparsers = tomcat_parser.add_subparsers(dest="tomcat_action", help="Tomcat actions")

    tomcat_start_parser = tomcat_subparsers.add_parser("start", help="Start tomcat application")
    tomcat_start_parser.add_argument("module", help="Sample module to start")
    tomcat_start_parser.add_argument("--build", default="false", help="Build before starting (true or false)")

    tomcat_subparsers.add_parser("stop", help="Stop Spring Boot application")

    # Spring subcommand
    spring_parser = subparsers.add_parser("spring", help="Manage Spring Boot applications")
    spring_subparsers = spring_parser.add_subparsers(dest="spring_action", help="Spring actions")

    spring_start_parser = spring_subparsers.add_parser("start", help="Start Spring Boot application")
    spring_start_parser.add_argument("module", help="Sample module to start")
    spring_start_parser.add_argument("--agent", default="false", help="Use Java agent (true or false)")
    spring_start_parser.add_argument("--auto-init", default="true", help="Auto-init agent (true or false)")
    spring_start_parser.add_argument("--build", default="false", help="Build before starting (true or false)")

    spring_stop_parser = spring_subparsers.add_parser("stop", help="Stop Spring Boot application")

    spring_wait_parser = spring_subparsers.add_parser("wait", help="Wait for Spring Boot application to be ready")
    spring_wait_parser.add_argument("--timeout", type=int, default=20, help="Max attempts to wait (default: 20)")

    spring_status_parser = spring_subparsers.add_parser("status", help="Check Spring Boot application status")

    # Sentry subcommand
    sentry_parser = subparsers.add_parser("sentry", help="Manage Sentry mock server")
    sentry_subparsers = sentry_parser.add_subparsers(dest="sentry_action", help="Sentry actions")

    sentry_start_parser = sentry_subparsers.add_parser("start", help="Start Sentry mock server")
    sentry_stop_parser = sentry_subparsers.add_parser("stop", help="Stop Sentry mock server")
    sentry_status_parser = sentry_subparsers.add_parser("status", help="Check Sentry mock server status")

    # Status subcommand
    status_parser = subparsers.add_parser("status", help="Show status of all services")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        return 1

    runner = SystemTestRunner()

    # Set up signal handlers for cleanup
    signal.signal(signal.SIGINT, runner.cleanup_on_exit)
    signal.signal(signal.SIGTERM, runner.cleanup_on_exit)

    try:
        if args.command == "test":
            # Convert true/false arguments to internal 1/0 format
            agent = str_to_bool(args.agent)
            auto_init = args.auto_init  # already accepts true/false
            build = str_to_bool(args.build)

            if args.manual_test and args.module:
                return runner.run_manual_test_mode(args.module, agent, auto_init, build)
            elif args.manual_test and args.all:
                print("Error: --manual-test requires a specific --module, cannot be used with --all")
                return 1
            elif args.manual_test and args.interactive:
                print("Error: --manual-test cannot be used with --interactive")
                return 1
            elif args.all:
                return runner.run_all_tests()
            elif args.module:
                return runner.run_single_test(args.module, agent, auto_init, build)
            elif args.interactive:
                return runner.run_interactive_tests(agent, auto_init, build)

        elif args.command == "tomcat":
            if args.tomcat_action == "start":
                # Convert true/false arguments to internal format
                build = str_to_bool(args.build)

                runner.start_tomcat_server(args.module)

                # Build if requested
                if build == "1":
                    print("Building before starting Tomcat application")
                    build_result = runner.build_module(args.module, ServerType.TOMCAT)
                    if build_result != 0:
                        print("Build failed")
                        return build_result

                if runner.wait_for_tomcat(args.module):
                    print("Tomcat application started successfully!")
                    return 0
                else:
                    print("Tomcat application failed to start!")
                    return 1
            elif args.tomcat_action == "stop":
                runner.stop_tomcat_server()
                print("Tomcat application stopped.")
                return 0

        elif args.command == "spring":
            if args.spring_action == "start":
                # Convert true/false arguments to internal format
                agent = str_to_bool(args.agent)
                auto_init = args.auto_init  # already accepts true/false
                build = str_to_bool(args.build)

                # Build if requested
                if build == "1":
                    print("Building before starting Spring application")
                    build_result = runner.build_module(args.module, ServerType.SPRING)
                    if build_result != 0:
                        print("Build failed")
                        return build_result

                runner.start_spring_server(args.module, agent, auto_init)
                if runner.wait_for_spring():
                    print("Spring application started successfully!")
                    return 0
                else:
                    print("Spring application failed to start!")
                    return 1
            elif args.spring_action == "stop":
                runner.stop_spring_server()
                print("Spring application stopped.")
                return 0
            elif args.spring_action == "wait":
                if runner.wait_for_spring(max_attempts=args.timeout):
                    print("Spring application is ready!")
                    return 0
                else:
                    print("Spring application is not ready!")
                    return 1
            elif args.spring_action == "status":
                status = runner.get_spring_status()
                print(f"Spring Boot Application Status:")
                print(f"  PID: {status['pid'] or 'None'}")
                print(f"  Process Running: {'✅' if status['process_running'] else '❌'}")
                print(f"  HTTP Ready: {'✅' if status['http_ready'] else '❌'}")
                return 0 if (status['process_running'] and status['http_ready']) else 1
            else:
                spring_parser.print_help()
                return 1

        elif args.command == "sentry":
            if args.sentry_action == "start":
                runner.start_sentry_mock_server()
                print("Sentry mock server started successfully!")
                return 0
            elif args.sentry_action == "stop":
                runner.stop_sentry_mock_server()
                print("Sentry mock server stopped.")
                return 0
            elif args.sentry_action == "status":
                status = runner.get_sentry_status()
                print(f"Sentry Mock Server Status:")
                print(f"  PID: {status['pid'] or 'None'}")
                print(f"  Process Running: {'✅' if status['process_running'] else '❌'}")
                print(f"  HTTP Ready: {'✅' if status['http_ready'] else '❌'}")
                return 0 if (status['process_running'] and status['http_ready']) else 1
            else:
                sentry_parser.print_help()
                return 1

        elif args.command == "status":
            runner.print_status_summary()
            return 0
        else:
            parser.print_help()
            return 1

    except KeyboardInterrupt:
        print("\nInterrupted by user")
        return 1
    except Exception as e:
        print(f"Error: {e}")
        return 1
    finally:
        # Only cleanup if running tests or manual test mode, not for individual start/stop commands
        if args.command == "test":
            runner.stop_spring_server()
            runner.stop_sentry_mock_server()

if __name__ == "__main__":
    sys.exit(main())
