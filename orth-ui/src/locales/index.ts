import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import enCommon from './en/common.json';
import enLogin from './en/login.json';
import enDashboard from './en/dashboard.json';
import enJob from './en/job.json';
import enLog from './en/log.json';
import enExecutor from './en/executor.json';
import enUser from './en/user.json';
import enGlue from './en/glue.json';

import zhCommon from './zh/common.json';
import zhLogin from './zh/login.json';
import zhDashboard from './zh/dashboard.json';
import zhJob from './zh/job.json';
import zhLog from './zh/log.json';
import zhExecutor from './zh/executor.json';
import zhUser from './zh/user.json';
import zhGlue from './zh/glue.json';

i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
        resources: {
            en: {
                common: enCommon,
                login: enLogin,
                dashboard: enDashboard,
                job: enJob,
                log: enLog,
                executor: enExecutor,
                user: enUser,
                glue: enGlue,
            },
            zh: {
                common: zhCommon,
                login: zhLogin,
                dashboard: zhDashboard,
                job: zhJob,
                log: zhLog,
                executor: zhExecutor,
                user: zhUser,
                glue: zhGlue,
            },
        },
        fallbackLng: 'en',
        defaultNS: 'common',
        interpolation: {
            escapeValue: false,
        },
        detection: {
            order: ['localStorage', 'navigator'],
            caches: ['localStorage'],
        },
    });

export default i18n;
