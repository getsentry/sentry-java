This folder contains the Maestro flows for the Sentry Android SDK.

## Running the Flows

First, make sure you have the Maestro CLI installed. You can find the installation instructions in the [Maestro documentation](https://docs.maestro.dev/getting-started/installing-maestro).

To run the Maestro flows, navigate to this directory in your terminal and execute the following command:

```bash
maestro run <flow_name>.yaml
```

Replace `<flow_name>` with the name of the flow you want to run, such as `orientation_change_flow.yaml`.

## Available Flows

- `orientation_change_flow.yaml`: This flow tests the orientation change/window size change functionality for replays.