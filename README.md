# lupadoku

Document search application. Accesses Lupapiste MongoDB directly and Onkalo search API through HTTP.

# Configuration

lupadoku-config.edn has defaults for local development. You can overwrite necessary values by creating
lupadoku-config-local.edn which is included in .gitignore.

# Builds and deployment

Builds are done by ci.lupapiste.fi. Deployment is done with Ansible, code and configuration are in lupapiste-ansible
project. Deployment to dev, test, qa and production environments is handled by ci.lupapiste.fi

Production deployment occurs when a build from master branch is promoted in Jenkins UI.

# Development

Run Figwheel on the background for compiling ClojureScript:

    lein figwheel

Start a repl:

    lein repl

and run the application:

    (go)

Reload all changed (back-end) namespaces:

    (reset)

You should symlink document-search-commons under checkouts for front-end development, as figwheel expects to access
sources from there as well. Resources and styles are also located under document-search-commons. Run sass build for
continuous css build when altering styles:

    lein with-profile dev sass4clj auto

To extract strings for translation, create a symlink to translations.txt under document-search-commons

    ln -s ../document-search-commons/resources/translations.txt resources/translations.txt

 and then run

    lein extract-strings

## License

Copyright © 2023 Cloudpermit Oy

Distributed under the European Union Public Licence (EUPL) version 1.2.
