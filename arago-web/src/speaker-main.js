import { mount } from 'svelte';
import './app.css';
import Speaker from './Speaker.svelte';

const app = mount(Speaker, { target: document.getElementById('speaker') });

export default app;
