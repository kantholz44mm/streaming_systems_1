#!/usr/bin/python3

import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation, PillowWriter
import json
import math
from math import pi
import numpy as np

samples = []

with open("ss/resources/grouped_distance_data.almost_json", "r") as file:
    lines = [json.loads(line) for line in file.readlines()]
    gda = [(int(l["group"]), float(l["data"]["distanz"]), float(l["data"]["winkel"]) * (pi / 180.0)) for l in lines]
    samples.extend(gda)

# Collect unique groups (in order)
groups = sorted({g for g, _, _ in samples})

MAX_RANGE = max([d for _, d, _ in samples])

fig, ax = plt.subplots()
ax.set_aspect("equal", adjustable="box")
ax.set_xlabel("X")
ax.set_ylabel("Y")
ax.axhline(0)
ax.axvline(0)
ax.set_xlim(-MAX_RANGE, MAX_RANGE)
ax.set_ylim(-MAX_RANGE, MAX_RANGE)

scatter = ax.scatter([], [])

def update(group_id):
    pts = [(d, a) for g, d, a in samples if g == group_id]

    if len(pts) == 0:
        scatter.set_offsets(np.empty((0, 2)))
        return scatter,

    xs = [d * math.cos(a) for d, a in pts]
    ys = [d * math.sin(a) for d, a in pts]

    scatter.set_offsets(np.column_stack([xs, ys]))
    ax.set_title(f"group {group_id}")

    return scatter,


ani = FuncAnimation(
    fig,
    update,
    frames=groups,
    interval=100,   # ms per frame
    repeat=True
)

writer = PillowWriter(fps=10)  # 100 ms per frame = 10 fps

ani.save("output.gif", writer=writer)