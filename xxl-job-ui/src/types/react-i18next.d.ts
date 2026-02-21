import 'react-i18next';

import type common from '../locales/en/common.json';
import type login from '../locales/en/login.json';
import type dashboard from '../locales/en/dashboard.json';
import type job from '../locales/en/job.json';
import type log from '../locales/en/log.json';
import type executor from '../locales/en/executor.json';
import type user from '../locales/en/user.json';
import type glue from '../locales/en/glue.json';

declare module 'react-i18next' {
    interface CustomTypeOptions {
        defaultNS: 'common';
        resources: {
            common: typeof common;
            login: typeof login;
            dashboard: typeof dashboard;
            job: typeof job;
            log: typeof log;
            executor: typeof executor;
            user: typeof user;
            glue: typeof glue;
        };
    }
}
