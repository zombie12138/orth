import { useMemo } from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { python } from '@codemirror/lang-python';
import { javascript } from '@codemirror/lang-javascript';
import { java } from '@codemirror/lang-java';
import { php } from '@codemirror/lang-php';
import { StreamLanguage } from '@codemirror/language';
import { shell } from '@codemirror/legacy-modes/mode/shell';
import { useThemeStore } from '../../../store/themeStore';

interface Props {
  value: string;
  onChange: (value: string) => void;
  glueType: string;
  readOnly?: boolean;
}

function getLanguageExtension(glueType: string) {
  switch (glueType) {
    case 'GLUE_PYTHON':
      return [python()];
    case 'GLUE_SHELL':
    case 'GLUE_POWERSHELL':
      return [StreamLanguage.define(shell)];
    case 'GLUE_GROOVY':
      return [java()]; // Groovy syntax close to Java
    case 'GLUE_NODEJS':
      return [javascript()];
    case 'GLUE_PHP':
      return [php()];
    default:
      return [];
  }
}

export default function CodeEditor({ value, onChange, glueType, readOnly }: Props) {
  const resolved = useThemeStore((s) => s.resolved);
  const extensions = useMemo(() => getLanguageExtension(glueType), [glueType]);

  return (
    <CodeMirror
      value={value}
      onChange={onChange}
      extensions={extensions}
      theme={resolved}
      height="calc(100vh - 200px)"
      style={{ fontSize: 13 }}
      readOnly={readOnly}
    />
  );
}
