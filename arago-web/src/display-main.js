import { mount } from 'svelte';
import './app.css';
import Display from './Display.svelte';

const app = mount(Display, { target: document.getElementById('display') });

export default app;
