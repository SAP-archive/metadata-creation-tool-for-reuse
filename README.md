# Metadata Creation Tool for REUSE

## About this project

This tool automatically creates the REUSE metadata files (dep5, licenses) automatically depending on the declared licenses in an existing repository. It is available as standalone Docker container and can be included in your project as GitHub Action.

These REUSE metadata files are put in a newly created branch called `reuse-metadata-proposal` of the repository it was run for. Afterwards, project maintainers can review and adjust the proposal in this branch and integrate them into the main branch once the data is complete and accurate.

## Requirements and Setup

### GitHub Personal Access Token

You need a personal access token with at least `repo` access rights for the repository that you want to create the REUSE metadata files for. Please see the [GitHub documentation on personal access tokens](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) if you need assistance.

### Clone the source code locally

Ensure to have cloned this repository to a local directory.

### Build the Docker container

The default way of using the metadata creation tool is to use it in a Docker Container. Ensure to have a running [Docker runtime](https://docs.docker.com/engine/) installed, then simply build the container using the command `docker build --tag reuse-metadata-creation .` in the local root directory of this project.

### Run the Docker container

After the container is built successfully, you can simply run it with the command `docker run -e GITHUB_ACCESS_TOKEN=<your-access-token> -e COPYRIGHT_OWNER=<copyright-owner-info> -e UPSTREAM_CONTACT=<upstream-contact-info> reuse-metadata-creation <github-repository-url>`. Alternatively, you can of course also use a file containing the environment variables.

- `COPYRIGHT_OWNER` should contain the person or entity that holds the copyright for the respective repository, e.g. 'SAP SE or an SAP affiliate company'
- `UPSTREAM_CONTACT` should contain the name and e-mail address of the contact for the respective repository, e.g. 'SAP Open Source Program Office <ospo@sap.com>'.

### Setup a GitHub Action

The tool is prepared to run as a GitHub Action. Create a workflow runner file in the respective repository under `.github/workflows`, e.g. called `reuse.yaml`. A working sample content would look like that:

```
name: "Launch Metadata Creation Tool for REUSE"
on:
  workflow_dispatch: ~

jobs:
  create_metadata_proposal:
    runs-on: ubuntu-latest
    name: "Metadata Creation Tool"
    steps:
      - uses: SAP/metadata-creation-tool-for-reuse@main
        with:
          repository_url: "${{ github.server_url }}/${{ github.repository }}"
          access_token: "${{ secrets.REUSE_ACCESS_TOKEN }}"
          copyright_owner: "Your name comes here"
          upstream_contact: "Your name comes here <yourname@yourdomain.something>"
```

The required personal access token is stored in the [secrets of the respective repository](https://docs.github.com/en/actions/security-guides/encrypted-secrets) as `REUSE_ACCESS_TOKEN`. If you store it under another name, ensure to have the variable name adjusted here as well.

## Support, Feedback, Contributing

This project is open to feature requests/suggestions, bug reports etc. via [GitHub issues](https://github.com/SAP/metadata-creation-tool-for-reuse/issues). Contribution and feedback are encouraged and always welcome. For more information about how to contribute, the project structure, as well as additional contribution information, see our [Contribution Guidelines](CONTRIBUTING.md).

## Code of Conduct

We as members, contributors, and leaders pledge to make participation in our community a harassment-free experience for everyone. By participating in this project, you agree to abide by its [Code of Conduct](CODE_OF_CONDUCT.md) at all times.

## Licensing

Copyright 2022 SAP SE or an SAP affiliate company and metadata-creation-tool-for-reuse contributors. Please see our [LICENSE](LICENSE) for copyright and license information. Detailed information including third-party components and their licensing/copyright information is available [via the REUSE tool](https://api.reuse.software/info/github.com/SAP/metadata-creation-tool-for-reuse).
