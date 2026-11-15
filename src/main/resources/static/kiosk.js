/*
 * Kiosk page behaviour.
 *
 * How it works:
 *   1. The ID input is kept focused at all times, so a barcode scan (which behaves like
 *      typing the ID and pressing Enter) always lands in it - even if someone clicked
 *      elsewhere on the screen.
 *   2. On Enter, the ID is sent to POST /api/kiosk/scan as JSON, and the input is cleared
 *      immediately (so the ID isn't left on screen).
 *   3. The result is shown in large text with a colour, then after RESET_AFTER_MS the
 *      screen returns to "Ready" for the next person. A new scan replaces the current
 *      result straight away - nobody has to wait.
 *
 * Safety notes for maintainers:
 *   - Always use textContent (never innerHTML) to put server data on the page. That way a
 *     name containing "<script>" is shown as text instead of being run as code.
 *   - Don't put the student ID in the URL, local storage, or console.log - it's a FERPA
 *     education record identifier.
 */
(function () {
    'use strict';

    /** How long a result stays on screen before resetting, in milliseconds. */
    const RESET_AFTER_MS = 5000;

    /** Headline text for each status returned by the API (see ScanOutcome.java). */
    const HEADLINES = {
        SIGNED_IN: 'Signed IN',
        SIGNED_OUT: 'Signed OUT',
        ALREADY_SIGNED_IN: 'Already signed in',
        ALREADY_SIGNED_OUT: 'Already signed out',
        UNKNOWN_ID: 'Not recognized',
        ERROR: 'Try again'
    };

    const form = document.getElementById('scan-form');
    const input = document.getElementById('student-id');
    const panel = document.getElementById('result');
    const headline = document.getElementById('result-headline');
    const nameLine = document.getElementById('result-name');
    const hoursLine = document.getElementById('result-hours');
    const messageLine = document.getElementById('result-message');

    let resetTimer = null;

    // ---- Keep the input focused --------------------------------------------------------

    function keepFocus() {
        if (document.activeElement !== input) {
            input.focus();
        }
    }

    window.addEventListener('load', keepFocus);
    document.addEventListener('click', keepFocus);
    // When the input loses focus, take it back on the next tick.
    input.addEventListener('blur', () => setTimeout(keepFocus, 0));
    // Belt and braces: re-check once a second (e.g. after the window regains focus).
    setInterval(keepFocus, 1000);

    // ---- Display -----------------------------------------------------------------------

    /** Shows a result. `data` has the shape of KioskController.ScanResponse. */
    function show(data) {
        const status = HEADLINES[data.status] ? data.status : 'ERROR';
        panel.dataset.status = status;
        headline.textContent = HEADLINES[status];
        nameLine.textContent = data.displayName || '';
        hoursLine.textContent = data.todayHours ? 'Today: ' + data.todayHours : '';
        messageLine.textContent = data.message || '';

        clearTimeout(resetTimer);
        resetTimer = setTimeout(showIdle, RESET_AFTER_MS);
    }

    /** Returns the screen to its waiting state. */
    function showIdle() {
        panel.dataset.status = 'IDLE';
        headline.textContent = 'Ready';
        nameLine.textContent = '';
        hoursLine.textContent = '';
        messageLine.textContent = 'Scan your badge to sign in or out.';
    }

    // ---- Scanning ----------------------------------------------------------------------

    async function submitScan(studentId) {
        try {
            const response = await fetch('api/kiosk/scan', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                body: JSON.stringify({ studentId: studentId }),
                cache: 'no-store'
            });
            // Error responses from our API are JSON too; anything else becomes a generic error.
            let data;
            try {
                data = await response.json();
            } catch (parseError) {
                data = { status: 'ERROR', message: 'Please scan again.' };
            }
            show(data);
        } catch (networkError) {
            show({ status: 'ERROR', message: 'Cannot reach the server. Please tell a mentor.' });
        }
    }

    form.addEventListener('submit', (event) => {
        event.preventDefault(); // stop the browser doing a normal page-reloading form submit
        const studentId = input.value.trim();
        input.value = '';       // clear right away so the ID isn't left on screen
        keepFocus();
        if (studentId) {
            submitScan(studentId);
        }
    });
})();
